$(document).ready(function() {
    const searchInput = $("#search");
    const loadingImage = $("#search-loading-image");

    searchInput.on("focus", function() {
        searchInput.select();
    });

    searchInput.on("keyup", function(event) {
        const value = searchInput.val();
        searchInput.css("text-transform", (value.length == 64 ? "uppercase" : "none"));
    });

    searchInput.on("keypress", function(event) {
        const value = searchInput.val();
        if (value.length == 0) { return true; }

        const key = event.which;
        if (key != KeyCodes.ENTER) { return true; }

        loadingImage.css("visibility", "visible");
        Api.search({ query: value }, function(data) {
            loadingImage.css("visibility", "hidden");

            const wasSuccess = data.wasSuccess;
            const errorMessage = data.errorMessage;
            const objectType = data.objectType;
            const object = data.object;

            if (wasSuccess) {
                if ( (objectType == Constants.BLOCK) || (objectType == Constants.BLOCK_HEADER) ) {
                    Ui.renderBlock(object);
                }
                else if (objectType == Constants.ADDRESS) {
                    Ui.renderAddress(object);
                }
                else if (objectType == Constants.TRANSACTION) {
                    Ui.renderTransaction(object);
                }
                else {
                    Console.log("Unknown ObjectType: " + objectType);
                }
            }
            else {
               console.log(errorMessage);
            }
        });

        searchInput.blur();

        return false;
    });   

    const queryParams = new URLSearchParams(window.location.search);
    if (queryParams.has("search")) {
        searchInput.val(queryParams.get("search"));
        searchInput.trigger($.Event( "keypress", { which: KeyCodes.ENTER } ));
    }

    window.onpopstate = function(event) {
        const state = event.state;
        if (state && state.hash) {
            searchInput.val(state.hash);
            searchInput.trigger($.Event( "keypress", { which: KeyCodes.ENTER } ));
        }
        else {
            searchInput.val("");

            const main = $("#main");
            main.empty();
        }
    };
});

