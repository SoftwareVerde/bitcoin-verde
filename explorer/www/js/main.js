var webSocket = null;

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

    if (window.location.protocol == "http:") {
        webSocket = new WebSocket("ws://" + window.location.host + "/api/v1/announcements");
    }
    else {
        webSocket = new WebSocket("wss://" + window.location.host + "/api/v1/announcements");
    }

    webSocket.onopen = function() { };

    webSocket.onmessage = function(event) {
        const message = JSON.parse(event.data);
        const objectType = message.objectType;

        let container = null;
        let element  = null;
        if ( (objectType == "TRANSACTION") || (objectType == "TRANSACTION_HASH") ) {
            const transaction = message.object;

            container = $("#main .recent-transactions");
            element = Ui.inflateTransaction(transaction);
            element.off("click");
        }
        else if (objectType == "BLOCK") {
            const blockHeader = message.object;

            container = $("#main .recent-blocks");
            element = Ui.inflateBlock(blockHeader);
            const blockLink = $(".hash .value", element);
            blockLink.toggleClass("clickable", true);
            blockLink.on("click", Ui._makeNavigateToBlockEvent(blockHeader.hash));
            element.off("click");
        }

        if (container != null && element != null) {
            const childrenElements = container.children();
            if (childrenElements.length > 9) {
                childrenElements.last().remove();
            }

            container.prepend(element);
            HashResizer.update(container);
        }

        return false;
    };

    webSocket.onclose = function() {
        console.log("WebSocket closed...");
    };

    const postTransactionInput = $("#post-transaction-input");
    const postTransactionButton = $("#post-transaction-button");
    postTransactionButton.on("click", function() {
        const transactionData = postTransactionInput.val();
        Api.postTransaction({ transactionData: transactionData }, function(result) {
            if (result.wasSuccess) {
                postTransactionInput.val("");
            }
        });
    });
});

