var Dialog = {};

Dialog.create = function(title, body, confirm, deny) {
    if (Dialog.div == null) Dialog.init();

    Dialog.confirm  = confirm;
    Dialog.deny     = deny;

    $("#dialog_title", Dialog.div).text(title); // Set the dialog title
    // Create Dialog Cancel Button
    var cancel_button = $("<div>X</div>");
    cancel_button.addClass("dialog_cancel_button");
    cancel_button.bind("click", function() {
        Dialog.close(false);
    });
    $("#dialog_title", Dialog.div).append(cancel_button); // Set the dialog title
    // End Create Dialog Cancel Button
    
    $("#dialog_body", Dialog.div).html(body);   // Set the dialog body

    if (typeof confirm === "function") {
        var button = $("<input type=\"button\" value=\"Okay\" />");
        button.addClass("dialog-button");
        button.bind("click", function() {
            Dialog.close(true);
        });
        $("#dialog_body", Dialog.div).append(button);
        setTimeout(function() {
            button.focus();
        }, 100);
    }
    if (typeof deny === "function") {
        var button = $("<input type=\"button\" value=\"Cancel\" />");
        button.addClass("dialog-button");
        button.bind("click", function() {
            Dialog.close(false);
        });
        $("#dialog_body", Dialog.div).append(button);
    }

    Dialog.div.finish().fadeIn(250);
}
Dialog.init = function() {
    $("body").append("<div id=\"dialog\"><div id=\"dialog_title\"></div><div id=\"dialog_body\"></div></div>");

    $("body").bind("keydown", function(e) {
        if (!$(Dialog.div).is(":visible")) return;
        var code = (e.keyCode ? e.keyCode : e.which);
        // Escape
        if (code == 27) {
            Dialog.close();
        }
    });

    Dialog.div = $("#dialog");
};
Dialog.close = function(result) {
    Dialog.div.finish().fadeOut(500);
    if (result) {
        if (typeof Dialog.confirm === "function") Dialog.confirm();
    }
    else {
        if (typeof Dialog.deny === "function") Dialog.deny();
    }
};
Dialog.div = null;
