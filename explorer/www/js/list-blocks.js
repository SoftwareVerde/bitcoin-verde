class ListBlocksUi {
    static search(objectHash) {
        document.location.href = "/?search=" + window.encodeURIComponent(objectHash);
    }
}

$(document).ready(function() {
    const searchInput = $("#search");
    const loadingImage = $("#search-loading-image");

    searchInput.keypress(function(event) {
        const key = event.which;

        if (key == KeyCodes.ENTER) {
            loadingImage.css("visibility", "visible");
            const hash = searchInput.val();
            ListBlocksUi.search(hash);
            return false;  
        }
    });   

    loadingImage.css("visibility", "visible");

    const parameters = {
        blockHeight: null,
        maxBlockCount: 25
    };

    Api.listBlockHeaders(parameters, function(data) {
        loadingImage.css("visibility", "hidden");

        const wasSuccess = data.wasSuccess;
        const errorMessage = data.errorMessage;
        const blockHeaders = data.blockHeaders;

        if (wasSuccess) {
            $("#main > div:not(.table-header)").remove();

            for (const i in blockHeaders) {
                const blockHeader = blockHeaders[i];
                const blockUi = Ui.inflateBlock(blockHeader);

                const blockHashSpan = $(".block-header .hash .value", blockUi);
                blockHashSpan.toggleClass("clickable", true);
                blockHashSpan.on("click", function(event) {
                    ListBlocksUi.search(blockHeader.hash);
                });

                blockUi.hide();
                $("#main").append(blockUi);
                blockUi.fadeIn(500);
            }
        }
        else {
           console.log(errorMessage);
        }
    });

});

