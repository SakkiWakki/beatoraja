package bms.player.beatoraja.launcher.gdx;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;

public class SpinnerWidget extends Table {

    private final TextField textField;
    private final TextButton decButton;
    private final TextButton incButton;
    private final Cell<?> decCell;
    private final Cell<?> textCell;
    private final Cell<?> incCell;

    private float value;
    private float min;
    private float max;
    private float step;
    private boolean integerMode;

    public SpinnerWidget(Skin skin, float min, float max, float initialValue, float step) {
        super(skin);
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = clamp(initialValue);
        this.integerMode = (step == Math.floor(step)) && (min == Math.floor(min)) && (max == Math.floor(max));

        decButton = new TextButton("-", skin);
        incButton = new TextButton("+", skin);
        textField = new TextField(formatValue(), skin);
        textField.setAlignment(Align.center);

        decButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                setValue(value - SpinnerWidget.this.step);
            }
        });

        incButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                setValue(value + SpinnerWidget.this.step);
            }
        });

        textField.setTextFieldListener((tf, c) -> {
            if (c == '\n' || c == '\r') {
                parseAndSet(tf.getText());
            }
        });

        textField.setTextFieldFilter((tf, c) -> {
            if (c == '-' || c == '.' || (c >= '0' && c <= '9')) return true;
            return false;
        });

        decCell = add(decButton).width(30).height(28);
        textCell = add(textField).width(80).height(28).padLeft(2).padRight(2);
        incCell = add(incButton).width(30).height(28);
    }

    private float clamp(float v) {
        return Math.max(min, Math.min(max, v));
    }

    private String formatValue() {
        if (integerMode) {
            return String.valueOf((int) value);
        }
        return String.format("%.2f", value);
    }

    private void parseAndSet(String text) {
        try {
            float parsed = Float.parseFloat(text);
            setValue(parsed);
        } catch (NumberFormatException ignored) {
            textField.setText(formatValue());
        }
    }

    public float getValue() {
        return value;
    }

    public int getIntValue() {
        return (int) value;
    }

    public void setValue(float newValue) {
        float old = this.value;
        this.value = clamp(newValue);
        textField.setText(formatValue());
        if (old != this.value) {
            fire(new ChangeListener.ChangeEvent());
        }
    }

    public void setRange(float min, float max) {
        this.min = min;
        this.max = max;
        setValue(value);
    }

    public TextField getTextField() {
        return textField;
    }

    public SpinnerWidget setCompact(float buttonWidth, float textWidth, float height) {
        decCell.width(buttonWidth).height(height);
        textCell.width(textWidth).height(height).padLeft(1).padRight(1);
        incCell.width(buttonWidth).height(height);
        return this;
    }
}
