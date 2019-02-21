$(document).ready(function() {
    const loadingImage = $("#search-loading-image");

    Api.getPrototypeBlock({ }, function(data) {
        loadingImage.css("visibility", "hidden");

        const wasSuccess = data.wasSuccess;
        const errorMessage = data.errorMessage;
        const object = data.block;

        if (wasSuccess) {
            Ui.renderBlock(object);
            $("#main .block .transaction:first-child").trigger("click");
        }
        else {
           console.log(errorMessage);
        }
    });
});
