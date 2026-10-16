package de.steffen.kreuz2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int OPEN_IMAGE = 10, OPEN_DOCUMENT = 11, EXPORT_DOCUMENT = 12, REPLACE_ORIGINAL = 13;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<String> history = new ArrayDeque<>();
    private PuzzleStore store;
    private Puzzle puzzle;
    private Bitmap bitmap;
    private ClueSearch.Reader clueReader = new ClueSearch.OnDeviceReader();
    private int lastTouchedCell = -1;
    private PuzzleView board;
    private TextView status;
    private EditText keyboard, columnsInput, rowsInput;
    private DirectionIndicator directionIndicator;
    private String compositionBase;
    private LinearLayout cropPanel;
    private ProgressBar progress;
    private Button openButton, moreButton, undoButton;
    private CheckBox gridCheck, autoCheck;
    private boolean busy, destroyed, changingControls, editingCrop;
    private int revision, savedRevision = -1;
    private String exportState;
    private Bitmap exportBitmap;
    private java.io.File exportOriginal;
    private final Runnable autoSave = () -> persist(false);
    private static final String[] DIRECTIONS = { "→ Waagerecht", "↓ Senkrecht", "← Nach links", "↑ Nach oben" };
    private static final class Session {
        final Puzzle puzzle; final Bitmap bitmap;
        Session(Puzzle p, Bitmap b) { puzzle = p; bitmap = b; }
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        store = new PuzzleStore(this);
        buildUi();
        Session retained = (Session) getLastNonConfigurationInstance();
        if (retained != null) showPuzzle(retained.puzzle, retained.bitmap, true);
        else {
            String id = getPreferences(MODE_PRIVATE).getString("lastPuzzle", null);
            if (id != null) load(id);
        }
    }
    @Override public Object onRetainNonConfigurationInstance() { return puzzle == null ? null : new Session(puzzle, bitmap); }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setGravity(Gravity.CENTER_VERTICAL); return layout; }
    private TextView text(String value, int sp) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(sp); view.setTextColor(0xff163c4c); return view;
    }
    private Button button(String label, LinearLayout parent, View.OnClickListener action) {
        Button b = new Button(this); b.setText(label); b.setTextSize(11); b.setAllCaps(false); b.setMinHeight(0); b.setMinimumHeight(0); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(dp(7), 0, dp(7), 0); b.setOnClickListener(action); parent.addView(b, new LinearLayout.LayoutParams(-2, dp(34))); return b;
    }
    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xfff6f9fa);
        root.setPadding(dp(2), 0, dp(2), 0);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(dp(2) + insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    dp(2) + insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        HorizontalScrollView actionsScroll = new HorizontalScrollView(this); actionsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout actions = row(); actionsScroll.addView(actions); root.addView(actionsScroll);
        openButton = button("Bild öffnen", actions, v -> openFile(false));
        LinearLayout options = actions;
        autoCheck = new CheckBox(this); autoCheck.setText("Auto"); autoCheck.setTextSize(11); autoCheck.setMinHeight(0); autoCheck.setPadding(0, 0, dp(2), 0); options.addView(autoCheck, new LinearLayout.LayoutParams(-2, dp(34)));
        autoCheck.setOnCheckedChangeListener((v, checked) -> { if (!changingControls && puzzle != null) { finishInput(); puzzle.automatic = checked; changed(); } });
        gridCheck = new CheckBox(this); gridCheck.setText("Linien"); gridCheck.setTextSize(11); gridCheck.setMinHeight(0); gridCheck.setPadding(0, 0, dp(2), 0); options.addView(gridCheck, new LinearLayout.LayoutParams(-2, dp(34)));
        gridCheck.setOnCheckedChangeListener((v, checked) -> { if (!changingControls && puzzle != null) { finishInput(); puzzle.showGrid = checked; changed(); } });
        undoButton = button("↶", options, v -> undo()); undoButton.setContentDescription("Letzte Eingabe rückgängig machen");
        moreButton = button("Mehr ⋮", options, this::more);
        status = text("Bild öffnen · Fragetext 1 Sek. halten: Google", 11); status.setSingleLine(true); status.setEllipsize(android.text.TextUtils.TruncateAt.END); status.setPadding(dp(4), 0, dp(4), 0);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setIndeterminate(true); progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        board = new PuzzleView(this); root.addView(board, new LinearLayout.LayoutParams(-1, 0, 1));
        board.setListener(new PuzzleView.Listener() {
            @Override public void selected() {
                if (busy || puzzle == null) return;
                finishInput(); changed();
                if (puzzle.valid(puzzle.selected)) {
                    keyboard.requestFocus(); ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(keyboard, InputMethodManager.SHOW_IMPLICIT);
                }
            }
            @Override public void touched(int cell) { lastTouchedCell = cell; }
            @Override public void imageError(String message) { if (!destroyed) toast(message); }
            @Override public void longPress(int cell) {
                if (busy || puzzle == null) return;
                lastTouchedCell = cell;
                if (puzzle.types[cell] == Puzzle.BLOCK) editCell(cell); else searchClue(cell);
            }
        });
        cropPanel = new LinearLayout(this); cropPanel.setOrientation(LinearLayout.VERTICAL); cropPanel.setVisibility(View.GONE);
        cropPanel.addView(text("Raster: Ecken ziehen, Anzahl prüfen, übernehmen.", 12));
        LinearLayout sizes = row();
        columnsInput = numberInput("Spalten", sizes); rowsInput = numberInput("Zeilen", sizes);
        button("Übernehmen", sizes, v -> applyCrop()); button("Abbruch", sizes, v -> endCrop()); cropPanel.addView(sizes); root.addView(cropPanel);
        TextWatcher preview = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
            public void afterTextChanged(Editable s) {
                int cols = readCount(columnsInput), rows = readCount(rowsInput);
                if (cols > 0 && rows > 0) board.preview(cols, rows);
            }
        };
        columnsInput.addTextChangedListener(preview); rowsInput.addTextChangedListener(preview);
        LinearLayout typing = row();
        directionIndicator = new DirectionIndicator(this);
        directionIndicator.setOnClickListener(v -> toggleDirection());
        directionIndicator.setOnLongClickListener(v -> { chooseDirection(); return true; });
        typing.addView(directionIndicator, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LetterInput letterInput = new LetterInput(this);
        keyboard = letterInput;
        letterInput.setListener(new LetterInput.Listener() {
            public void text(String value) { typeText(value); }
            public void erase() { eraseGrid(); }
            public void composing(String value) { previewComposition(value); }
            public void finishComposition() { compositionBase = null; }
        });
        keyboard.setSingleLine(true); keyboard.setTextSize(16); keyboard.setHint("Hier Buchstaben eingeben");
        keyboard.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        keyboard.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        keyboard.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int dir = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? Puzzle.RIGHT : keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? Puzzle.DOWN
                    : keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? Puzzle.LEFT : keyCode == KeyEvent.KEYCODE_DPAD_UP ? Puzzle.UP : -1;
            if (dir >= 0 && puzzle != null) {
                finishInput();
                puzzle.direction = dir; int next = puzzle.neighbor(puzzle.selected, dir);
                if (puzzle.valid(next)) puzzle.selected = next;
                changed(); return true;
            }
            return false;
        });
        typing.addView(keyboard, new LinearLayout.LayoutParams(0, dp(48), 1)); button("⌫", typing, v -> erase()); root.addView(typing); root.addView(status, new LinearLayout.LayoutParams(-1, dp(16)));
        setContentView(root); refresh();
    }
    private EditText numberInput(String label, LinearLayout parent) {
        LinearLayout group = new LinearLayout(this); group.setOrientation(LinearLayout.VERTICAL);
        group.addView(text(label, 11)); EditText field = new EditText(this); field.setInputType(InputType.TYPE_CLASS_NUMBER); field.setSingleLine(); field.setTextSize(15);
        field.setSelectAllOnFocus(true); group.addView(field, new LinearLayout.LayoutParams(-1, dp(42)));
        parent.addView(group, new LinearLayout.LayoutParams(0, -2, 1)); return field;
    }
    private int readCount(EditText field) {
        try { int n = Integer.parseInt(field.getText().toString()); return n > 0 && n <= 100 ? n : -1; }
        catch (NumberFormatException e) { return -1; }
    }
    private void refresh() {
        boolean has = puzzle != null;
        openButton.setEnabled(!busy && !editingCrop); moreButton.setEnabled(!busy && !editingCrop);
        autoCheck.setEnabled(has && !busy && !editingCrop); gridCheck.setEnabled(has && !busy && !editingCrop);
        keyboard.setEnabled(has && !busy && !editingCrop); undoButton.setEnabled(!history.isEmpty() && !busy && !editingCrop);
        directionIndicator.setEnabled(has && !busy && !editingCrop);
        board.setEnabled(!busy);
        changingControls = true; autoCheck.setChecked(has && puzzle.automatic); gridCheck.setChecked(has && puzzle.showGrid); changingControls = false;
        if (has) {
            directionIndicator.setDirection(puzzle.direction);
            if (!busy) status.setText(puzzle.cols + " Sp. × " + puzzle.rows + " Z. · " + puzzle.filled() + " Buchst."
                    + (puzzle.valid(puzzle.selected) ? " · Z" + (puzzle.selected / puzzle.cols + 1) + "/S" + (puzzle.selected % puzzle.cols + 1) : "")
                    + (revision == savedRevision ? " · gespeichert" : " · speichert …"));
        }
        board.invalidate();
    }
    private void setBusy(boolean value, String message) {
        busy = value; progress.setVisibility(value ? View.VISIBLE : View.GONE); refresh(); if (value) status.setText(message);
    }
    private void changed() { revision++; refresh(); board.revealSelection(); scheduleSave(); }
    private void scheduleSave() { handler.removeCallbacks(autoSave); handler.postDelayed(autoSave, 800); }
    private void remember() {
        if (puzzle == null) return;
        try { history.addLast(PuzzleStore.toJson(puzzle).toString()); while (history.size() > 50) history.removeFirst(); }
        catch (Exception e) { error(e); }
    }
    private void undo() {
        if (history.isEmpty()) return;
        finishInput();
        try { puzzle = PuzzleStore.fromJson(new JSONObject(history.removeLast())); board.setPuzzle(puzzle, bitmap, false); changed(); }
        catch (Exception e) { error(e); }
    }
    private void typeText(String value) { typeText(value, true); }
    private void typeText(String value, boolean record) {
        if (puzzle == null || busy || editingCrop) return;
        if (!puzzle.valid(puzzle.selected)) { toast("Bitte zuerst ein Feld antippen."); return; }
        String input = value.toUpperCase(Locale.GERMAN);
        boolean recorded = false;
        for (int at = 0; at < input.length();) {
            int cp = input.codePointAt(at); at += Character.charCount(cp);
            if (Character.isLetterOrDigit(cp)) {
                if (!recorded) { if (record) remember(); recorded = true; }
                puzzle.put(new String(Character.toChars(cp)));
            }
        }
        if (recorded || !record) changed();
    }
    private void previewComposition(String value) {
        if (puzzle == null || busy || editingCrop) return;
        try {
            if (compositionBase == null) {
                if (!puzzle.valid(puzzle.selected)) return;
                remember(); compositionBase = PuzzleStore.toJson(puzzle).toString();
            }
            puzzle = PuzzleStore.fromJson(new JSONObject(compositionBase));
            board.setPuzzle(puzzle, bitmap, false);
            typeText(value, false);
        } catch (Exception e) { compositionBase = null; error(e); }
    }
    private void finishInput() { if (keyboard instanceof LetterInput) ((LetterInput) keyboard).resetInput(); compositionBase = null; }
    private void toggleDirection() {
        if (puzzle == null || busy || editingCrop) return;
        finishInput(); remember(); puzzle.direction = puzzle.direction % 2 == 0 ? Puzzle.DOWN : Puzzle.RIGHT; changed();
    }
    private void erase() { finishInput(); eraseGrid(); }
    private void eraseGrid() { if (puzzle != null && !busy && !editingCrop && puzzle.valid(puzzle.selected)) { remember(); puzzle.backspace(); changed(); } }
    private void chooseDirection() {
        if (puzzle == null || busy) return;
        finishInput();
        new AlertDialog.Builder(this).setTitle("Schreibrichtung").setSingleChoiceItems(DIRECTIONS, puzzle.direction, (d, index) -> {
            remember(); puzzle.direction = index; changed(); d.dismiss();
        }).setNegativeButton("Abbrechen", null).show();
    }
    private void more(View anchor) {
        finishInput();
        PopupMenu menu = new PopupMenu(this, anchor);
        String[] labels = {"Raster korrigieren", "Automatisch neu erkennen", "Ansicht einpassen", "Spielstand exportieren", "Spielstand importieren", "Hilfe", "Feldtyp/Pfeil bearbeiten", "Spielstand speichern", "Spielstände öffnen", "Originalbild erneut zuordnen"};
        for (int i = 0; i < labels.length; i++) menu.getMenu().add(0, i, i, labels[i]).setEnabled(i >= 4 || puzzle != null);
        menu.getMenu().findItem(6).setEnabled(puzzle != null && (lastTouchedCell >= 0 || puzzle.selected >= 0));
        menu.getMenu().findItem(7).setEnabled(puzzle != null);
        menu.getMenu().findItem(9).setEnabled(puzzle != null);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0: startCrop(); break;
                case 1: new AlertDialog.Builder(this).setTitle("Neu erkennen?").setMessage("Raster und Feldkorrekturen werden ersetzt. Buchstaben werden nach ihrer Bildposition übernommen, soweit das neue Raster passt.")
                        .setPositiveButton("Erkennen", (d, w) -> redetect()).setNegativeButton("Abbrechen", null).show(); break;
                case 2: board.fitImage(); break;
                case 3: export(); break;
                case 4: openFile(true); break;
                case 6: editCell(lastTouchedCell >= 0 ? lastTouchedCell : puzzle.selected); break;
                case 7: nameAndSave(); break;
                case 8: savedGames(); break;
                case 9: chooseOriginal(); break;
                default: help();
            }
            return true;
        }); menu.show();
    }
    private void openFile(boolean document) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(document ? "*/*" : "image/*");
        if (document) intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"application/json", "application/octet-stream"});
        try { startActivityForResult(intent, document ? OPEN_DOCUMENT : OPEN_IMAGE); }
        catch (Exception e) { error(e); }
    }
    private void chooseOriginal() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
        try { startActivityForResult(intent, REPLACE_ORIGINAL); } catch (Exception e) { error(e); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == EXPORT_DOCUMENT) { finishExport(uri); return; }
        if (request == REPLACE_ORIGINAL) { replaceOriginal(uri); return; }
        if (request != OPEN_IMAGE && request != OPEN_DOCUMENT) return;
        setBusy(true, request == OPEN_IMAGE ? "Bild wird geladen und Raster erkannt …" : "Spielstand wird geöffnet …");
        worker.execute(() -> {
            try {
                byte[] bytes;
                try (InputStream in = getContentResolver().openInputStream(uri)) { bytes = PuzzleStore.readLimited(in, request == OPEN_DOCUMENT ? PuzzleStore.MAX_DOCUMENT_BYTES : PuzzleStore.MAX_IMAGE_BYTES); }
                Bitmap image; Puzzle found; boolean detected = true;
                byte[] original;
                if (request == OPEN_DOCUMENT) { PuzzleStore.Loaded loaded = PuzzleStore.importDocument(bytes); image = loaded.bitmap; found = loaded.puzzle; original = loaded.original; }
                else {
                    image = PuzzleStore.decode(bytes); found = analyze(image, null);
                    original = bytes;
                    if (found == null) {
                        detected = false;
                        found = new Puzzle(Puzzle.evenly(0, image.getWidth() - 1, 10), Puzzle.evenly(0, image.getHeight() - 1, 10));
                    }
                }
                if (original != null) store.saveOriginal(found.id, original);
                final Puzzle p = found; final Bitmap b = image; final boolean recognized = detected;
                ui(() -> {
                    showPuzzle(p, b, true); setBusy(false, ""); persist(false);
                    if (!recognized) {
                        startCrop(); new AlertDialog.Builder(this).setTitle("Raster nicht sicher erkannt")
                                .setMessage("Ziehe die vier Ecken auf das Rätsel und gib Spalten und Zeilen an. Das angezeigte 10 × 10 ist nur ein Platzhalter.")
                                .setPositiveButton("Verstanden", null).show();
                    } else if (request == OPEN_IMAGE) {
                        toast(p.cols + " Spalten und " + p.rows + " Zeilen erkannt. Bitte mit den Linien prüfen.");
                    }
                });
            } catch (Exception | OutOfMemoryError e) { ui(() -> { setBusy(false, ""); error(e); }); }
        });
    }
    private Puzzle analyze(Bitmap image, Puzzle manual) {
        float factor = Math.min(1f, 1200f / Math.max(image.getWidth(), image.getHeight()));
        Bitmap small = factor == 1 ? image : Bitmap.createScaledBitmap(image, Math.max(1, Math.round(image.getWidth() * factor)), Math.max(1, Math.round(image.getHeight() * factor)), true);
        float sx = small.getWidth() / (float) image.getWidth(), sy = small.getHeight() / (float) image.getHeight();
        int[] pixels = new int[small.getWidth() * small.getHeight()]; small.getPixels(pixels, 0, small.getWidth(), 0, 0, small.getWidth(), small.getHeight());
        GridDetector detector = new GridDetector(pixels, small.getWidth(), small.getHeight());
        Puzzle result;
        if (manual == null) result = detector.detect();
        else {
            float[] xs = manual.xs.clone(), ys = manual.ys.clone();
            for (int i = 0; i < xs.length; i++) xs[i] *= sx;
            for (int i = 0; i < ys.length; i++) ys[i] *= sy;
            result = new Puzzle(xs, ys); detector.classify(result);
        }
        if (result != null) {
            for (int i = 0; i < result.xs.length; i++) result.xs[i] /= sx;
            for (int i = 0; i < result.ys.length; i++) result.ys[i] /= sy;
        }
        if (small != image) small.recycle();
        return result;
    }
    private void showPuzzle(Puzzle p, Bitmap b, boolean reset) {
        finishInput();
        lastTouchedCell = -1;
        puzzle = p; bitmap = b; history.clear(); revision++; savedRevision = -1;
        getPreferences(MODE_PRIVATE).edit().putString("lastPuzzle", p.id).apply();
        editingCrop = false; cropPanel.setVisibility(View.GONE); board.setPuzzle(p, b, reset);
        board.setOriginalImage(store.originalFile(p.id)); refresh();
    }
    private void load(String id) {
        setBusy(true, "Spielstand wird geladen …");
        worker.execute(() -> {
            try { PuzzleStore.Loaded loaded = store.load(id); ui(() -> { showPuzzle(loaded.puzzle, loaded.bitmap, true); savedRevision = revision; setBusy(false, ""); }); }
            catch (Exception | OutOfMemoryError e) { ui(() -> { setBusy(false, ""); error(e); }); }
        });
    }
    private void persist(boolean announce) {
        if (puzzle == null || bitmap == null) return;
        handler.removeCallbacks(autoSave);
        try {
            puzzle.updated = System.currentTimeMillis(); String state = PuzzleStore.toJson(puzzle).toString();
            String id = puzzle.id; Bitmap image = bitmap; int version = revision;
            worker.execute(() -> {
                try { store.save(state, image, id); ui(() -> {
                    if (puzzle != null && puzzle.id.equals(id)) { savedRevision = version; refresh(); }
                    if (announce) toast("Spielstand lokal gespeichert.");
                }); }
                catch (Exception e) { ui(() -> { if (status != null) status.setText("Speichern fehlgeschlagen. Bitte erneut versuchen."); error(e); }); }
            });
        } catch (Exception e) { error(e); }
    }
    private void nameAndSave() {
        if (puzzle == null) return;
        finishInput();
        EditText name = new EditText(this); name.setSingleLine(); name.setText(puzzle.name); name.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("Spielstand speichern").setView(name).setPositiveButton("Speichern", (d, w) -> {
            String title = name.getText().toString().trim(); puzzle.name = title.isEmpty() ? "Mein Kreuzworträtsel" : title.substring(0, Math.min(100, title.length()));
            revision++; persist(true);
        }).setNegativeButton("Abbrechen", null).show();
    }
    private void savedGames() {
        if (puzzle != null) persist(false);
        setBusy(true, "Spielstände werden geladen …");
        worker.execute(() -> {
            List<PuzzleStore.Entry> entries = store.list();
            ui(() -> {
                setBusy(false, "");
                if (entries.isEmpty()) { toast("Noch keine gespeicherten Rätsel. Über Mehr kannst du einen Spielstand importieren."); return; }
                String[] labels = new String[entries.size()];
                for (int i = 0; i < labels.length; i++) labels[i] = entries.get(i).name + "\n" + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new java.util.Date(entries.get(i).updated));
                new AlertDialog.Builder(this).setTitle("Spielstände auf diesem Gerät").setItems(labels, (d, which) -> load(entries.get(which).id)).setNegativeButton("Schließen", null).show();
            });
        });
    }
    private void export() {
        if (puzzle == null) return;
        try {
            exportState = PuzzleStore.toJson(puzzle).toString(); exportBitmap = bitmap; exportOriginal = store.originalFile(puzzle.id);
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, puzzle.name.replaceAll("[^\\p{L}\\p{N} _-]", "_") + ".kreuz2.json");
            startActivityForResult(intent, EXPORT_DOCUMENT);
        } catch (Exception e) { error(e); }
    }
    private void finishExport(Uri uri) {
        // Android can recreate the Activity while the document picker is visible.
        final String state;
        try { state = exportState != null ? exportState : puzzle == null ? null : PuzzleStore.toJson(puzzle).toString(); }
        catch (Exception e) { error(e); return; }
        final Bitmap image = exportBitmap != null ? exportBitmap : bitmap;
        if (state == null || image == null) { toast("Bitte den Spielstand laden und den Export erneut starten."); return; }
        setBusy(true, "Spielstand mit Bild wird exportiert …");
        final java.io.File source = exportOriginal;
        worker.execute(() -> {
            try {
                Puzzle exported = PuzzleStore.fromJson(new JSONObject(state));
                byte[] bytes = PuzzleStore.export(exported, image, source != null ? source : store.originalFile(exported.id));
                try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) throw new java.io.IOException("Zieldatei nicht verfügbar."); out.write(bytes);
                }
                ui(() -> { setBusy(false, ""); toast("Spielstand mit Bild exportiert."); });
            } catch (Exception | OutOfMemoryError e) { ui(() -> { setBusy(false, ""); error(e); }); }
            finally { exportState = null; exportBitmap = null; exportOriginal = null; }
        });
    }
    private void startCrop() {
        if (puzzle == null) return;
        hideKeyboard(); editingCrop = true; board.startCrop();
        columnsInput.setText(String.valueOf(puzzle.cols)); rowsInput.setText(String.valueOf(puzzle.rows));
        cropPanel.setVisibility(View.VISIBLE); refresh();
    }
    private void replaceOriginal(Uri uri) {
        if (puzzle == null || bitmap == null) return;
        String id = puzzle.id; int previewWidth = bitmap.getWidth(), previewHeight = bitmap.getHeight();
        setBusy(true, "Originalbild wird ohne Verkleinerung übernommen …");
        worker.execute(() -> {
            try {
                byte[] bytes;
                try (InputStream in = getContentResolver().openInputStream(uri)) { bytes = PuzzleStore.readLimited(in, PuzzleStore.MAX_IMAGE_BYTES); }
                android.graphics.BitmapFactory.Options bounds = PuzzleStore.imageBounds(bytes);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new java.io.IOException("Keine unterstützte Bilddatei.");
                double ratio = (bounds.outWidth / (double) bounds.outHeight) / (previewWidth / (double) previewHeight);
                if (Math.abs(ratio - 1) > .02) throw new java.io.IOException("Das Seitenverhältnis passt nicht zum bisherigen Hintergrund. Bitte dieselbe Originaldatei mit demselben Bildausschnitt auswählen.");
                store.saveOriginal(id, bytes);
                ui(() -> {
                    setBusy(false, "");
                    if (puzzle != null && puzzle.id.equals(id)) board.setOriginalImage(store.originalFile(id));
                    toast("Originalbild übernommen. Beim Zoomen werden die Originaldetails geladen. Deine Eingaben bleiben erhalten.");
                });
            } catch (Exception | OutOfMemoryError e) { ui(() -> { setBusy(false, ""); error(e); }); }
        });
    }
    private void endCrop() { hideKeyboard(); editingCrop = false; board.endCrop(); cropPanel.setVisibility(View.GONE); refresh(); }
    private void applyCrop() {
        int cols = readCount(columnsInput), rows = readCount(rowsInput);
        if (cols < 1 || rows < 1) { toast("Bitte jeweils 1 bis 100 angeben."); return; }
        if (board.crop.width() / cols < 4 || board.crop.height() / rows < 4) { toast("Die Felder sind zu klein. Bitte Rasterbereich oder Anzahl korrigieren."); return; }
        try {
            Puzzle manual = new Puzzle(Puzzle.evenly(board.crop.left, board.crop.right, cols), Puzzle.evenly(board.crop.top, board.crop.bottom, rows));
            endCrop(); replaceDetection(manual);
        } catch (Exception e) { error(e); }
    }
    private void redetect() { replaceDetection(null); }
    private void replaceDetection(Puzzle manual) {
        if (puzzle == null) return;
        Puzzle previous = puzzle; Bitmap image = bitmap; setBusy(true, "Raster und Felder werden ausgewertet …");
        worker.execute(() -> {
            try {
                Puzzle found = analyze(image, manual);
                ui(() -> {
                    setBusy(false, "");
                    if (found == null) { toast("Kein sicheres Raster. Bitte Raster manuell korrigieren."); startCrop(); return; }
                    remember(); found.id = previous.id; found.name = previous.name; found.automatic = previous.automatic; found.showGrid = true;
                    // Transfer letters only when both cell centers still coincide closely.
                    for (int i = 0; i < previous.types.length; i++) if (!previous.letters[i].isEmpty()) {
                        int c = i % previous.cols, r = i / previous.cols;
                        float x = (previous.xs[c] + previous.xs[c + 1]) / 2, y = (previous.ys[r] + previous.ys[r + 1]) / 2;
                        for (int nc = 0; nc < found.cols; nc++) if (x >= found.xs[nc] && x < found.xs[nc + 1]) {
                            for (int nr = 0; nr < found.rows; nr++) if (y >= found.ys[nr] && y < found.ys[nr + 1]) {
                                int target = nr * found.cols + nc;
                                if (Math.abs(x - (found.xs[nc] + found.xs[nc + 1]) / 2) < (found.xs[nc + 1] - found.xs[nc]) * .35f
                                        && Math.abs(y - (found.ys[nr] + found.ys[nr + 1]) / 2) < (found.ys[nr + 1] - found.ys[nr]) * .35f) {
                                    found.types[target] = Puzzle.OPEN; found.arrows[target] = 0; found.letters[target] = previous.letters[i];
                                }
                            }
                        }
                    }
                    puzzle = found; board.setPuzzle(puzzle, bitmap, false); changed();
                });
            } catch (Exception | OutOfMemoryError e) { ui(() -> { setBusy(false, ""); error(e); }); }
        });
    }
    private void editCell(int cell) {
        if (puzzle == null || editingCrop || cell < 0 || cell >= puzzle.types.length) return;
        finishInput();
        String[] labels = { "Schreibfeld", "Schwarzes Feld / gesperrt", "Hinweisfeld ohne Pfeil", "Hinweis →", "Hinweis ↓", "Hinweis → und ↓", "Hinweis ←", "Hinweis ↑" };
        new AlertDialog.Builder(this).setTitle("Feld: Zeile " + (cell / puzzle.cols + 1) + ", Spalte " + (cell % puzzle.cols + 1))
                .setItems(labels, (d, which) -> {
                    remember(); puzzle.types[cell] = which == 0 ? Puzzle.OPEN : which == 1 ? Puzzle.BLOCK : Puzzle.CLUE;
                    int[] masks = {0, 0, 0, 1, 2, 3, 4, 8}; puzzle.arrows[cell] = masks[which];
                    if (which != 0) puzzle.letters[cell] = "";
                    if (puzzle.selected == cell && which != 0) puzzle.selected = -1;
                    changed();
                }).setNegativeButton("Abbrechen", null).show();
    }
    private void searchClue(int cell) {
        if (puzzle == null || bitmap == null || busy || editingCrop) return;
        finishInput(); hideKeyboard();
        setBusy(true, "Fragetext wird auf dem Gerät gelesen …");
        try {
            clueReader.read(bitmap, puzzle, cell, store.originalFile(puzzle.id), new ClueSearch.Callback() {
                public void success(String text) { ui(() -> {
                    setBusy(false, "");
                    if (ClueSearch.readable(text)) openGoogle(text); else manualClue(text, false);
                }); }
                public void failure(Exception error) { ui(() -> { setBusy(false, ""); manualClue("", true); }); }
            });
        } catch (Exception | OutOfMemoryError error) { setBusy(false, ""); manualClue("", true); }
    }
    private void manualClue(String recognized, boolean failed) {
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20), dp(8), dp(20), 0);
        content.addView(text(failed ? "Der Fragetext konnte nicht gelesen werden. Gib ihn für die Google-Suche ein."
                : "Kein sicher lesbarer Fragetext. Bitte den Suchtext ergänzen oder korrigieren.", 14));
        EditText query = new EditText(this); query.setHint("Fragetext"); query.setText(ClueSearch.clean(recognized));
        query.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES); content.addView(query);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Kreuzworträtsel-Lösung suchen").setView(content)
                .setPositiveButton("Google suchen", null).setNeutralButton("Feld bearbeiten", (d, w) -> editCell(lastTouchedCell))
                .setNegativeButton("Abbrechen", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = query.getText().toString();
            if (!ClueSearch.readable(value)) { query.setError("Bitte einen Fragetext mit mindestens zwei Buchstaben eingeben (max. 600 Zeichen)."); return; }
            dialog.dismiss(); openGoogle(value);
        }));
        dialog.show();
    }
    private void openGoogle(String clue) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, ClueSearch.googleUrl(clue))); }
        catch (android.content.ActivityNotFoundException error) { toast("Zum Öffnen der Google-Suche wird ein Browser benötigt."); }
    }
    private void help() {
        new AlertDialog.Builder(this).setTitle("So füllst du dein Rätsel aus")
                .setMessage("1. Bild öffnen: Wähle einen geraden Screenshot mit sichtbaren Rasterlinien.\n\n2. Prüfe die erkannte Anzahl und schalte Linien ein. Orange markiert Hinweisfelder, rot gesperrte Felder. Unter Mehr → Raster korrigieren kannst du die Ecken und die Anzahl anpassen.\n\n3. Tippe ein beliebiges Feld an und schreibe mit der Tastatur. Auch erkannte Frage- oder Sperrfelder lassen sich so überschreiben; die erste Eingabe macht sie zum Schreibfeld. Nochmals antippen oder der große Pfeil wechselt waagerecht/senkrecht. Lange auf den Pfeil drücken bietet alle vier Richtungen.\n\n4. Auto folgt erkannten Pfeilen aus benachbarten Hinweisfeldern und setzt am Wortende nach unten bzw. rechts fort. Auto ausschalten hält deine Richtung fest. Kleine Pfeile und Hinweistext werden nur anhand ihrer Bildform erkannt; kein zuverlässiges Lesen aller Rätselarten. Ein Feld antippen und Mehr → Feldtyp/Pfeil bearbeiten öffnet die Korrektur.\n\n5. Mit zwei Fingern vergrößern, mit einem Finger verschieben. Originaldetails werden beim Zoomen nachgeladen. Bei alten, unscharfen Spielständen über Mehr → Originalbild erneut zuordnen dieselbe Originaldatei wählen; die Eingaben bleiben erhalten. ↶ macht Eingaben oder Korrekturen rückgängig.\n\n6. Spielstände werden automatisch lokal gespeichert. Mehr → Spielstand speichern vergibt einen Namen. Mehr → Spielstand exportieren speichert eine Datei inklusive Bild; diese lässt sich später importieren. Lokale Spielstände gehen beim Deinstallieren verloren.\n\n7. Einen Fragetext eine Sekunde gedrückt halten: Die App liest den Ausschnitt lokal und öffnet Google mit dem Fragetext plus Kreuzworträtsel Lösung. Bei unleserlichem Text kannst du die Suchfrage eingeben. Für Google wird Internet benötigt; der Bildausschnitt wird nicht hochgeladen.\n\nDie App trägt keine gefundenen Lösungen automatisch ein.")
                .setPositiveButton("Verstanden", null).show();
    }
    private void hideKeyboard() {
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(keyboard.getWindowToken(), 0); keyboard.clearFocus();
    }
    private void ui(Runnable action) { handler.post(() -> { if (!destroyed) action.run(); }); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private void error(Throwable error) {
        String message = error.getMessage();
        new AlertDialog.Builder(this).setTitle("Das hat nicht geklappt")
                .setMessage(error instanceof OutOfMemoryError ? "Das Bild benötigt zu viel Speicher. Bitte einen kleineren Screenshot wählen." : message == null ? "Bitte erneut versuchen." : message)
                .setPositiveButton("OK", null).show();
    }
    @Override protected void onPause() { super.onPause(); board.cancelPendingHold(); if (puzzle != null) persist(false); }
    @Override protected void onDestroy() { destroyed = true; handler.removeCallbacks(autoSave); board.releaseImages(); worker.shutdown(); super.onDestroy(); }
}
