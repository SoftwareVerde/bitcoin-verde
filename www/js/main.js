$(document).ready(function() {
    const searchInput = $("#search");
    const loadingImage = $("#search-loading-image");
    searchInput.keypress(function(event) {
        const key = event.which;

        if (key == KeyCodes.ENTER) {
            loadingImage.css("visibility", "visible");
            Api.search({ hash: searchInput.val() }, function(data) {
                loadingImage.css("visibility", "hidden");

                const wasSuccess = data.wasSuccess;
                const errorMessage = data.errorMessage;
                const objectType = data.objectType;
                const object = data.object;

                if (wasSuccess) {
                    if ( (objectType == Constants.BLOCK) || (objectType == Constants.BLOCK_HEADER) ) {
                        Ui.renderBlock(object);
                    }
                    else {
                        Ui.renderTransaction(object);
                    }
                }
                else {
                   console.log(errorMessage);
                }
            });

            return false;  
        }
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

