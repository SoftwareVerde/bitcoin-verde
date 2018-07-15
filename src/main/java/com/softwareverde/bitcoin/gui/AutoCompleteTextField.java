package com.softwareverde.bitcoin.gui;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.util.*;

/**
 * Loosely based on gist by Caleb Brinkman (https://gist.github.com/floralvikings/10290131)
 */
public class AutoCompleteTextField extends TextField {
    private static final int DEFAULT_MAX_ENTRIES = 10;

    private final SortedSet<String> _entries;
    private ContextMenu _resultsPopup;
    private int _maxEntriesToDisplay;
    private boolean _hasSearchResults = false;

    public AutoCompleteTextField() {
        this(new HashSet<>(), DEFAULT_MAX_ENTRIES);
    }

    public AutoCompleteTextField(final Collection<String> initialEntries) {
        this(initialEntries, DEFAULT_MAX_ENTRIES);
    }

    public AutoCompleteTextField(final Collection<String> initialEntries, final int maxEntriesToDisplay) {
        _entries = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        _resultsPopup = new ContextMenu();
        _maxEntriesToDisplay = maxEntriesToDisplay;

        _entries.addAll(initialEntries);

        this.textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(final ObservableValue<? extends String> observable, final String oldValue, final String newValue) {
                _updateSearchResults();
            }
        });

        this.focusedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(final ObservableValue<? extends Boolean> observable, final Boolean oldValue, final Boolean newValue) {
                _resultsPopup.hide();
            }
        });
    }

    public void setEntries(Collection<String> newEntries) {
        _entries.clear();
        _entries.addAll(newEntries);
        _updateSearchResults();
    }

    public void addEntries(Collection<String> newEntries) {
        _entries.addAll(newEntries);
        _updateSearchResults();
    }

    public SortedSet<String> getEntries() {
        return _entries;
    }

    private void _updateSearchResults() {
        final String text = getText();
        if (text.length() == 0) {
            _resultsPopup.hide();
            _hasSearchResults = false;
        }
        else {
            final LinkedList<String> searchResults = new LinkedList<>();
            searchResults.addAll(_entries.subSet(text, text + Character.MAX_VALUE));
            if (searchResults.size() > 0) {
                _populatePopup(searchResults);
                _hasSearchResults = true;
                if (!_resultsPopup.isShowing()) {
                    _resultsPopup.show(AutoCompleteTextField.this, Side.BOTTOM, 0, 0);
                }
            }
            else {
                _hasSearchResults = false;
                _resultsPopup.hide();
            }
        }
    }

    private void _populatePopup(List<String> searchResults) {
        final List<CustomMenuItem> menuItems = new LinkedList<>();

        final int count = Math.min(searchResults.size(), _maxEntriesToDisplay);
        for (int i = 0; i < count; i++) {
            final String result = searchResults.get(i);
            final Label entryLabel = new Label(result);
            final CustomMenuItem item = new CustomMenuItem(entryLabel, true);
            item.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(final ActionEvent event) {
                    setText(result);
                    _resultsPopup.hide();
                }
            });
            menuItems.add(item);
        }
        _resultsPopup.getItems().clear();
        _resultsPopup.getItems().addAll(menuItems);
    }

    public boolean hasSearchResults() {
        return _hasSearchResults;
    }

    public boolean hasSelectedEntry() {
        final String currentText = textProperty().get();
        return _entries.contains(currentText);
    }
}