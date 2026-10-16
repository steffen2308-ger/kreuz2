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
    private static final int OPEN_IMAGE = 10, OPEN_DOCUMENT = 11, EXPORT_DOCUMENT = 12;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<String> history = new ArrayDeque<>();
    private PuzzleStore store;
    private Puzzle puzzle;
    private Bitmap bitmap;
    private PuzzleView board;
    private TextView status, position;
    private EditText keyboard, columnsInput, rowsInput;
    private LinearLayout cropPanel;
    private ProgressBar progress;
    private Button openButton, savedButton, saveButton, moreButton, directionButton, undoButton;
    private CheckBox gridCheck, autoCheck;
    private boolean busy, destroyed, changingControls, editingCrop;
    private int revision, savedRevision = -1;
    private String exportState;
    private Bitmap exportBitmap;
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
        Button b = new Button(this); b.setText(label); b.setTextSize(12); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(dp(10), 0, dp(10), 0); b.setOnClickListener(action); parent.addView(b, new LinearLayout.LayoutParams(-2, dp(44))); return b;
    }
    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xfff6f9fa);
        root.setPadding(dp(8), dp(4), dp(8), dp(4));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(dp(8) + insets.getSystemWindowInsetLeft(), dp(4) + insets.getSystemWindowInsetTop(),
                    dp(8) + insets.getSystemWindowInsetRight(), dp(4) + insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        TextView title = text("Kreuz2 · Screenshot ausfüllen", 20); title.setPadding(dp(4), dp(3), 0, dp(3)); root.addView(title);
        HorizontalScrollView actionsScroll = new HorizontalScrollView(this); actionsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout actions = row(); actionsScroll.addView(actions); root.addView(actionsScroll);
        openButton = button("Bild öffnen", actions, v -> openFile(false));
        savedButton = button("Spielstände", actions, v -> savedGames());
        saveButton = button("Speichern", actions, v -> nameAndSave());
        moreButton = button("Mehr ⋮", actions, this::more);
        HorizontalScrollView optionsScroll = new HorizontalScrollView(this); optionsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout options = row(); optionsScroll.addView(options); root.addView(optionsScroll);
        directionButton = button(DIRECTIONS[0], options, v -> {
            if (puzzle == null) return; remember();
            puzzle.direction = puzzle.direction % 2 == 0 ? Puzzle.DOWN : Puzzle.RIGHT; changed();
        });
        directionButton.setOnLongClickListener(v -> { chooseDirection(); return true; });
        autoCheck = new CheckBox(this); autoCheck.setText("Auto"); autoCheck.setTextSize(13); options.addView(autoCheck);
        autoCheck.setOnCheckedChangeListener((v, checked) -> { if (!changingControls && puzzle != null) { puzzle.automatic = checked; changed(); } });
        gridCheck = new CheckBox(this); gridCheck.setText("Linien"); gridCheck.setTextSize(13); options.addView(gridCheck);
        gridCheck.setOnCheckedChangeListener((v, checked) -> { if (!changingControls && puzzle != null) { puzzle.showGrid = checked; changed(); } });
        undoButton = button("↶ Zurück", options, v -> undo());
        status = text("Ein Bild öffnen oder einen Spielstand importieren.", 12); status.setPadding(dp(4), dp(3), dp(4), dp(4)); root.addView(status);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setIndeterminate(true); progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        board = new PuzzleView(this); root.addView(board, new LinearLayout.LayoutParams(-1, 0, 1));
        board.setListener(new PuzzleView.Listener() {
            @Override public void selected() {
                if (busy || puzzle == null) return;
                refresh(); scheduleSave();
                if (puzzle.open(puzzle.selected)) {
                    keyboard.requestFocus(); ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(keyboard, InputMethodManager.SHOW_IMPLICIT);
                }
            }
            @Override public void longPress(int cell) { if (!busy) editCell(cell); }
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
        position = text("Feld antippen · lange drücken: Feldtyp/Pfeil", 12); position.setPadding(dp(4), dp(4), 0, dp(2)); root.addView(position);
        LinearLayout typing = row();
        LetterInput letterInput = new LetterInput(this);
        keyboard = letterInput;
        letterInput.setListener(new LetterInput.Listener() {
            public void text(String value) { typeText(value); }
            public void erase() { MainActivity.this.erase(); }
        });
        keyboard.setSingleLine(true); keyboard.setTextSize(16); keyboard.setHint("Hier Buchstaben eingeben");
        keyboard.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        keyboard.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        keyboard.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DEL && keyboard.length() == 0) { erase(); return true; }
            int dir = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? Puzzle.RIGHT : keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? Puzzle.DOWN
                    : keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? Puzzle.LEFT : keyCode == KeyEvent.KEYCODE_DPAD_UP ? Puzzle.UP : -1;
            if (dir >= 0 && puzzle != null) {
                puzzle.direction = dir; int next = puzzle.neighbor(puzzle.selected, dir);
                if (puzzle.open(next)) puzzle.selected = next;
                changed(); return true;
            }
            return false;
        });
        typing.addView(keyboard, new LinearLayout.LayoutParams(0, dp(48), 1)); button("⌫", typing, v -> erase()); root.addView(typing);
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
        openButton.setEnabled(!busy && !editingCrop); savedButton.setEnabled(!busy && !editingCrop); moreButton.setEnabled(!busy && !editingCrop);
        saveButton.setEnabled(has && !busy && !editingCrop); directionButton.setEnabled(has && !busy && !editingCrop);
        autoCheck.setEnabled(has && !busy && !editingCrop); gridCheck.setEnabled(has && !busy && !editingCrop);
        keyboard.setEnabled(has && !busy && !editingCrop); undoButton.setEnabled(!history.isEmpty() && !busy && !editingCrop);
        board.setEnabled(!busy);
        changingControls = true; autoCheck.setChecked(has && puzzle.automatic); gridCheck.setChecked(has && puzzle.showGrid); changingControls = false;
        if (has) {
            directionButton.setText(DIRECTIONS[puzzle.direction]);
            if (!busy) status.setText(puzzle.cols + " Spalten × " + puzzle.rows + " Zeilen · " + puzzle.filled() + " Buchstaben · "
                    + (revision == savedRevision ? "lokal gespeichert" : "wird gespeichert"));
            position.setText(puzzle.open(puzzle.selected) ? "Zeile " + (puzzle.selected / puzzle.cols + 1) + ", Spalte " + (puzzle.selected % puzzle.cols + 1)
                    + " · " + DIRECTIONS[puzzle.direction] : "Feld antippen · lange drücken: Feldtyp/Pfeil");
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
        try { puzzle = PuzzleStore.fromJson(new JSONObject(history.removeLast())); board.setPuzzle(puzzle, bitmap, false); changed(); }
        catch (Exception e) { error(e); }
    }
    private void typeText(String value) {
        if (puzzle == null || busy || editingCrop) return;
        if (!puzzle.open(puzzle.selected)) { toast("Bitte zuerst ein freies Feld antippen."); return; }
        String input = value.toUpperCase(Locale.GERMAN);
        boolean recorded = false;
        for (int at = 0; at < input.length();) {
            int cp = input.codePointAt(at); at += Character.charCount(cp);
            if (Character.isLetterOrDigit(cp)) {
                if (!recorded) { remember(); recorded = true; }
                puzzle.put(new String(Character.toChars(cp)));
            }
        }
        if (recorded) changed();
    }
    private void erase() { if (puzzle != null && !busy && !editingCrop && puzzle.open(puzzle.selected)) { remember(); puzzle.backspace(); changed(); } }
    private void chooseDirection() {
        if (puzzle == null || busy) return;
        new AlertDialog.Builder(this).setTitle("Schreibrichtung").setSingleChoiceItems(DIRECTIONS, puzzle.direction, (d, index) -> {
            remember(); puzzle.direction = index; changed(); d.dismiss();
        }).setNegativeButton("Abbrechen", null).show();
    }
    private void more(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        String[] labels = {"Raster korrigieren", "Automatisch neu erkennen", "Ansicht einpassen", "Spielstand exportieren", "Spielstand importieren", "Hilfe"};
        for (int i = 0; i < labels.length; i++) menu.getMenu().add(0, i, i, labels[i]).setEnabled(i >= 4 || puzzle != null);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0: startCrop(); break;
                case 1: new AlertDialog.Builder(this).setTitle("Neu erkennen?").setMessage("Raster und Feldkorrekturen werden ersetzt. Buchstaben werden nach ihrer Bildposition übernommen, soweit das neue Raster passt.")
                        .setPositiveButton("Erkennen", (d, w) -> redetect()).setNegativeButton("Abbrechen", null).show(); break;
                case 2: board.fitImage(); break;
                case 3: export(); break;
                case 4: openFile(true); break;
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
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == EXPORT_DOCUMENT) { finishExport(uri); return; }
        if (request != OPEN_IMAGE && request != OPEN_DOCUMENT) return;
        setBusy(true, request == OPEN_IMAGE ? "Bild wird geladen und Raster erkannt …" : "Spielstand wird geöffnet …");
        worker.execute(() -> {
            try {
                byte[] bytes;
                try (InputStream in = getContentResolver().openInputStream(uri)) { bytes = PuzzleStore.readLimited(in, 35 * 1024 * 1024); }
                Bitmap image; Puzzle found; boolean detected = true;
                if (request == OPEN_DOCUMENT) { PuzzleStore.Loaded loaded = PuzzleStore.importDocument(bytes); image = loaded.bitmap; found = loaded.puzzle; }
                else {
                    image = PuzzleStore.decode(bytes); found = analyze(image, null);
                    if (found == null) {
                        detected = false;
                        found = new Puzzle(Puzzle.evenly(0, image.getWidth() - 1, 10), Puzzle.evenly(0, image.getHeight() - 1, 10));
                    }
                }
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
        puzzle = p; bitmap = b; history.clear(); revision++; savedRevision = -1;
        getPreferences(MODE_PRIVATE).edit().putString("lastPuzzle", p.id).apply();
        editingCrop = false; cropPanel.setVisibility(View.GONE); board.setPuzzle(p, b, reset); refresh();
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
            exportState = PuzzleStore.toJson(puzzle).toString(); exportBitmap = bitmap;
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
        worker.execute(() -> {
            try {
                byte[] bytes = PuzzleStore.export(PuzzleStore.fromJson(new JSONObject(state)), image);
                try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) throw new java.io.IOException("Zieldatei nicht verfügbar."); out.write(bytes);
                }
                ui(() -> { setBusy(false, ""); toast("Spielstand mit Bild exportiert."); });
            } catch (Exception | OutOfMemoryError e) { ui(() -> { setBusy(false, ""); error(e); }); }
            finally { exportState = null; exportBitmap = null; }
        });
    }
    private void startCrop() {
        if (puzzle == null) return;
        hideKeyboard(); editingCrop = true; board.startCrop();
        columnsInput.setText(String.valueOf(puzzle.cols)); rowsInput.setText(String.valueOf(puzzle.rows));
        cropPanel.setVisibility(View.VISIBLE); refresh();
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
                                if (found.open(target) && Math.abs(x - (found.xs[nc] + found.xs[nc + 1]) / 2) < (found.xs[nc + 1] - found.xs[nc]) * .35f
                                        && Math.abs(y - (found.ys[nr] + found.ys[nr + 1]) / 2) < (found.ys[nr + 1] - found.ys[nr]) * .35f) found.letters[target] = previous.letters[i];
                            }
                        }
                    }
                    puzzle = found; board.setPuzzle(puzzle, bitmap, false); changed();
                });
            } catch (Exception | OutOfMemoryError e) { ui(() -> { setBusy(false, ""); error(e); }); }
        });
    }
    private void editCell(int cell) {
        if (puzzle == null || editingCrop) return;
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
    private void help() {
        new AlertDialog.Builder(this).setTitle("So füllst du dein Rätsel aus")
                .setMessage("1. Bild öffnen: Wähle einen geraden Screenshot mit sichtbaren Rasterlinien.\n\n2. Prüfe die erkannte Anzahl und schalte Linien ein. Orange markiert Hinweisfelder, rot gesperrte Felder. Unter Mehr → Raster korrigieren kannst du die Ecken und die Anzahl anpassen.\n\n3. Tippe ein freies Feld an und schreibe mit der Tastatur. Nochmals antippen oder die Richtungstaste wechselt waagerecht/senkrecht. Lange auf die Richtungstaste drücken bietet alle vier Richtungen.\n\n4. Auto folgt erkannten Pfeilen aus benachbarten Hinweisfeldern und setzt am Wortende nach unten bzw. rechts fort. Auto ausschalten hält deine Richtung fest. Kleine Pfeile und Hinweistext werden nur anhand ihrer Bildform erkannt; kein zuverlässiges Lesen aller Rätselarten. Lange auf ein Feld drücken korrigiert Feldtyp und Pfeil.\n\n5. Mit zwei Fingern vergrößern, mit einem Finger verschieben. ↶ macht Eingaben oder Korrekturen rückgängig.\n\n6. Spielstände werden automatisch lokal gespeichert. Speichern vergibt einen Namen. Mehr → Spielstand exportieren speichert eine Datei inklusive Bild; diese lässt sich später importieren. Lokale Spielstände gehen beim Deinstallieren verloren.\n\nBilder und Eingaben bleiben auf deinem Gerät. Die App löst keine Wörter automatisch.")
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
    @Override protected void onPause() { super.onPause(); if (puzzle != null) persist(false); }
    @Override protected void onDestroy() { destroyed = true; handler.removeCallbacks(autoSave); worker.shutdown(); super.onDestroy(); }
}
