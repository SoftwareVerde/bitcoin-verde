$(window).on("load", function() {
    $(".toggle > .toggle-bar").on("click", function() {
        $(this).parent().toggleClass("off");
    });
});
