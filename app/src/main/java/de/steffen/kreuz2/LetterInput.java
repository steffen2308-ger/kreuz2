package de.steffen.kreuz2;

import android.annotation.SuppressLint;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

/** Shows IME composition immediately; replacements update the same grid cells. */
// The host intentionally uses a platform Activity/Material theme, not an AppCompat theme.
@SuppressLint("AppCompatCustomView")
public final class LetterInput extends EditText {
    public interface Listener {
        void text(String value);
        void erase();
        void composing(String value);
        void finishComposition();
    }
    private Listener listener;
    private String composition;
    private int connectionGeneration;
    public LetterInput(Context context) { super(context); }
    public void setListener(Listener value) { listener = value; }
    private void committed(CharSequence value) {
        if (listener != null && value != null && value.length() > 0) listener.text(value.toString());
        getText().clear();
    }
    private void preview(CharSequence value) {
        String text = value == null ? "" : value.toString();
        if (!text.equals(composition)) {
            composition = text;
            if (listener != null) listener.composing(text);
        }
        getText().clear();
    }
    private void finishComposition() {
        if (composition != null) {
            composition = null;
            if (listener != null) listener.finishComposition();
        }
    }
    private void eraseLetter() {
        if (composition != null && !composition.isEmpty()) {
            preview(composition.substring(0, composition.offsetByCodePoints(composition.length(), -1)));
        } else {
            finishComposition();
            if (listener != null) listener.erase();
        }
    }
    /** End the current word before changing selection; reject a late commit from the old IME connection. */
    public void resetInput() {
        finishComposition(); getText().clear(); connectionGeneration++;
        if (hasFocus()) ((InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).restartInput(this);
    }
    @Override public InputConnection onCreateInputConnection(EditorInfo attributes) {
        InputConnection original = super.onCreateInputConnection(attributes);
        if (original == null) return null;
        final int generation = connectionGeneration;
        return new InputConnectionWrapper(original, false) {
            @Override public boolean setComposingText(CharSequence text, int newCursorPosition) {
                if (generation != connectionGeneration) return false;
                preview(text); return true;
            }
            @Override public boolean commitText(CharSequence text, int newCursorPosition) {
                if (generation != connectionGeneration) return false;
                if (composition != null) { preview(text); finishComposition(); }
                else committed(text);
                return true;
            }
            @Override public boolean finishComposingText() {
                if (generation != connectionGeneration) return false;
                finishComposition(); return true;
            }
            @Override public boolean deleteSurroundingText(int before, int after) {
                if (generation != connectionGeneration) return false;
                if (before > 0 && length() == 0) { eraseLetter(); return true; }
                return super.deleteSurroundingText(before, after);
            }
            @Override public boolean deleteSurroundingTextInCodePoints(int before, int after) {
                if (generation != connectionGeneration) return false;
                if (before > 0 && length() == 0) { eraseLetter(); return true; }
                if (Build.VERSION.SDK_INT >= 24) return super.deleteSurroundingTextInCodePoints(before, after);
                return super.deleteSurroundingText(before, after);
            }
            @Override public boolean sendKeyEvent(KeyEvent event) {
                if (generation != connectionGeneration) return false;
                if (event.getKeyCode() == KeyEvent.KEYCODE_DEL && length() == 0) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) eraseLetter(); return true;
                }
                return super.sendKeyEvent(event);
            }
        };
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DEL && length() == 0) { eraseLetter(); return true; }
        int unicode = event.getUnicodeChar();
        if (!event.isCtrlPressed() && !event.isAltPressed() && Character.isLetterOrDigit(unicode)) {
            finishComposition();
            committed(new String(Character.toChars(unicode))); return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    @Override public boolean onTextContextMenuItem(int id) {
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
            finishComposition();
            ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null) committed(clipboard.getPrimaryClip().getItemAt(0).coerceToText(getContext()));
            return true;
        }
        return super.onTextContextMenuItem(id);
    }
}
