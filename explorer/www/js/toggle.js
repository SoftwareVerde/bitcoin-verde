$(window).on("load", function() {
    $(".toggle > .toggle-bar").on("click", function() {
        const toggleWidget = $(this).parent();
        const isOff = toggleWidget.hasClass("off");
        toggleWidget.toggleClass("off", (! isOff));
        toggleWidget.trigger("change", isOff);
    });
});
