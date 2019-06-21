# Bitcoin Verde Java Style Guide

## Preface

Many major open-source projects have its own style guide: a set of conventions
(sometimes arbitrary) about how to write code for that project. It is much
easier to understand a large codebase when all the code in it is a
consistent style.

This document is intended as a guide, not rulebook, for how Java code should be
styled and engineered for Bitcoin Verde.  This guide is a living document, and
therefore the guidelines recorded here may change, and may also not be all-
encompassing.  The guidelines are not only for stylistic conventions, but also
engineering conventions.  While this style guide is not infallible, nor is it
unbreakable, it should also not be ignored without a justifiable reason for
an exception to the rule.  It is the developer's responsibility to execute
sound judgment for when to deviate and when to not deviate from this style
guide.

Generally, this style guide prefers explicitness to implicitness.  Terse functions, classes, and variables are frowned upon, and reasonably descriptive names are encouraged.

## Stylistic Decisions

### Indentation

Indentation shall consist of four spaces, not tabs.

### Line Wrapping &amp; New Lines
Code should not wrap unless the line becomes unwieldy.  The 80-character ruler
convention is not to be considered unless creating a block comment.

Trivial statements may be compacted into a single line if it improves readability of the code.  For instance, an early return may be written as `if (inputWasInvalid) { return; }`, on a single line with braces.

### Braces

Braces should always be written, even if not required by the language.  Braces should on the same line as functions and other control statements.

Single branching statements such as `if (true) return;` should always include braces, as: `if (true) { return; }`

Braces may be used to separate thoughts or sub-processes in lieu of creating a function, as long as that function would not otherwise be reused or requires access to a scope that would be unwieldly to parameterize.  These braces are not preceded by a function or control statement, and therefore begin at the start of a line.

### Parenthesis

Parenthesis should be used whenever ambiguity could occur, within reason.  Understanding how the Java parser is written (and therefore remembering bind priorities of statements) should not be relied upon.  For instance, `(byteCount + bufferLength * 2)` should always be rewritten as `(byteCount + (bufferLength * 2))` to remove ambiguity of the intended design.

### Non-public Functions and Members

Private and protected functions and members should always be prefaced with an underscore.  If the class is in its own package, the `protected` access modifier should be used in lieu of `private`.  A notable exception to this paradigm is if the designer is certain that another developer, or his/her future self should definitely not override the method, then `private` should be used.

### Empty Statements

Empty statements should be marked as an empty braces pair on the same line as the originating statement, or in a regular block with a comment indicating "Nothing." or with a comment explaining why the code block is empty.

`while (list.pop() != null) { }`

```
while (list.pop() != null) {
    // Nothing.
}
```

### Explicit Generic Types

Explicit generic typing is required except when the type is easily inferred within its declaration.

```
final List<Item> items = new ArrayList<>(10); // Same-line construction may be inferred visually easily.
```

```
final List<Item> _items;
...
public Constructor() {
    _items = new ArrayList<Item>(10); // Should be explicitly provided due to low proximity.
}
```

## Engineering Decisions

### One Effect Per Line

Each effect should be on its own line.  This may result in creating additional variable statements, and if this result becomes distracting consider grouping the break-outs into a breathing statement with either a newline or scoping braces.

```
final TransactionId transactionId;
{
    final long rowId = row.getLong("id");
    transactionId = TransactionId.wrap(rowId);
}
```

### Use The Compiler To Your Advantage

#### Explicit Typing

Using the compiler to your advantage includes explicit typing, even when not strictly necessary.  For instance, TransactionId is a wrapper around a java.lang.Long.  However, ambiguity can be removed (and mistakes prevented) by function signatures requiring a TransactionId instead of the more ambiguous Long.  For example:

`public void addTransactionToBlock(final Long transactionId, final Long blockId) { /* ... */ }`

The invoker of this function could trivially switch the order of blockId and transactionId, which would cause an error that is hard to catch.  Alternatively, consider:

`public void addTransactionToBlock(final TransactionId transactionId, final BlockId blockId) { /* ... */ }`

With this function signature, the invoker switching the parameter order would have an error at compile time.

#### final Modifier and Constable

The final modifier prevents reassignment.  In conjunction with the `Constable` paradigm, the invariants within a scope become checked by the compiler as well as the developer.

For instance, if a list is not intended to be mutated by its current scope, we can ensure this by using a final reference and an immutable interface.

```
final List<Item> items = itemFactory.getItems();
items = itemFactory.getBetterItems(); // Invalid.
items.add(betterItem); // Invalid.
```

