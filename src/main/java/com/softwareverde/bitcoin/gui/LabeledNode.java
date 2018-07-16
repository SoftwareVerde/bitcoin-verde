package com.softwareverde.bitcoin.gui;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class LabeledNode<T extends Node>  extends VBox {
    protected final Label _label;
    protected final T _node;

    public LabeledNode(final String label, final T node) {
        super();

        _node = node;

        _label = new Label(label);
        _label.setLabelFor(_node);

        this.getChildren().add(_label);
        this.getChildren().add(_node);
    }

    public Label getLabel() {
        return _label;
    }

    public T getNode() {
        return _node;
    }
}
