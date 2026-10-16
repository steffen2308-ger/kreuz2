package de.steffen.kreuz2;

import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;

/** Process committed input once; keep IME composition (accents, suggestions) out of the grid. */
public final class LetterInput extends EditText {
    public interface Listener { void text(String value); void erase(); }
    private Listener listener;
    public LetterInput(Context context) { super(context); }
    public void setListener(Listener value) { listener = value; }
    private void committed(CharSequence value) {
        if (listener != null && value != null && value.length() > 0) listener.text(value.toString());
        getText().clear();
    }
    @Override public InputConnection onCreateInputConnection(EditorInfo attributes) {
        InputConnection original = super.onCreateInputConnection(attributes);
        if (original == null) return null;
        return new InputConnectionWrapper(original, false) {
            @Override public boolean commitText(CharSequence text, int newCursorPosition) {
                super.finishComposingText(); committed(text); return true;
            }
            @Override public boolean finishComposingText() {
                boolean result = super.finishComposingText();
                String text = getText().toString(); if (!text.isEmpty()) committed(text);
                return result;
            }
            @Override public boolean deleteSurroundingText(int before, int after) {
                if (before > 0 && length() == 0) { if (listener != null) listener.erase(); return true; }
                return super.deleteSurroundingText(before, after);
            }
            @Override public boolean deleteSurroundingTextInCodePoints(int before, int after) {
                if (before > 0 && length() == 0) { if (listener != null) listener.erase(); return true; }
                if (Build.VERSION.SDK_INT >= 24) return super.deleteSurroundingTextInCodePoints(before, after);
                return super.deleteSurroundingText(before, after);
            }
            @Override public boolean sendKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_DEL && length() == 0) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && listener != null) listener.erase(); return true;
                }
                return super.sendKeyEvent(event);
            }
        };
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DEL && length() == 0) { if (listener != null) listener.erase(); return true; }
        int unicode = event.getUnicodeChar();
        if (!event.isCtrlPressed() && !event.isAltPressed() && Character.isLetterOrDigit(unicode)) {
            committed(new String(Character.toChars(unicode))); return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    @Override public boolean onTextContextMenuItem(int id) {
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
            ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null) committed(clipboard.getPrimaryClip().getItemAt(0).coerceToText(getContext()));
            return true;
        }
        return super.onTextContextMenuItem(id);
    }
}
