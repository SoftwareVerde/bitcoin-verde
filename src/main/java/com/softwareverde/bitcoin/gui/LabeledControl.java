package com.softwareverde.bitcoin.gui;

import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class LabeledControl<T extends Control>  extends VBox {
    protected final Label _label;
    protected final T _control;

    public LabeledControl(final String label, final T control) {
        super();

        _control = control;

        _label = new Label(label);
        _label.setLabelFor(_control);

        this.getChildren().add(_label);
        this.getChildren().add(_control);
    }

    public Label getLabel() {
        return _label;
    }

    public T getControl() {
        return _control;
    }
}
