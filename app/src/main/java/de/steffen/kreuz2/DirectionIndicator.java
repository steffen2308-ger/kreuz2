package de.steffen.kreuz2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** A fixed-size, high-contrast direction arrow that stays visible above the keyboard. */
public final class DirectionIndicator extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private int direction;
    public DirectionIndicator(Context context) { super(context); setDirection(Puzzle.RIGHT); }
    public void setDirection(int value) {
        direction = value;
        String[] names = {"nach rechts", "nach unten", "nach links", "nach oben"};
        setContentDescription("Schreibrichtung: " + names[value] + ". Antippen zum Wechseln."); invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        canvas.save(); canvas.translate((getWidth() - size) / 2, (getHeight() - size) / 2); canvas.scale(size / 56, size / 56);
        paint.setColor(isEnabled() ? 0xff007c91 : 0xff78909c); paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(1, 1, 55, 55, 10, 10, paint);
        canvas.rotate(direction * 90, 28, 28);
        arrow.reset(); arrow.moveTo(10, 23); arrow.lineTo(31, 23); arrow.lineTo(31, 12);
        arrow.lineTo(48, 28); arrow.lineTo(31, 44); arrow.lineTo(31, 33); arrow.lineTo(10, 33); arrow.close();
        paint.setColor(0xffffffff); canvas.drawPath(arrow, paint); canvas.restore();
    }
}
