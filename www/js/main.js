class Http {
    static get(url, data, callbackFunction) {
        const query = Object.keys(data).map(key => encodeURIComponent(key) + "=" + encodeURIComponent(data[key])).join("&");

        const request = new Request(
            url + "?" + query,
            {
                method:         "GET",
                credentials:    "include",
            }
        );

        window.fetch(request, { credentials: "same-origin" })
            .then(function(response) {
                return response.json();
            })
            .then(function(json) {
                if (typeof callbackFunction == "function") {
                    callbackFunction(json);
                }
            });
    }

    static post(url, data, callbackFunction) {
        const request = new Request(
            url,
            {
                method:         "POST",
                credentials:    "include",
                body:           JSON.stringify(data)
            }
        );

        window.fetch(request, { credentials: "same-origin" })
            .then(function(response) {
                return response.json();
            })
            .then(function(json) {
                if (typeof callbackFunction == "function") {
                    callbackFunction(json);
                }
            });
    }
}

class Api {
    static search(hash, callback) {
        Http.get(Api.PREFIX + "search", { hash: hash }, callback);
    }
}
Api.PREFIX = "/api/v1/";

class Constants {
    static get BLOCK() { return "BLOCK"; }
    static get TRANSACTION() { return "TRANSACTION"; }
    static get SATOSHIS_PER_BITCOIN() { return 100000000; }
}

class KeyCodes {
    static get ENTER() { return 13; }
}

class UI {
    static renderBlock(block) {

        const loadingImage = $("#search-loading-image");

        const templates = $("#templates");
        const blockTemplate = $(".block", templates);
        const blockUi = blockTemplate.clone();

        $(".block-header .height .value", blockUi).text(block.height);
        $(".block-header .hash .value", blockUi).text(block.hash);
        $(".block-header .difficulty .mask .value", blockUi).text(block.difficulty.mask);
        $(".block-header .difficulty .ratio .value", blockUi).text(block.difficulty.ratio);
        const previousBlockHashSpan = $(".block-header .previous-block-hash .value", blockUi);
        previousBlockHashSpan.text(block.previousBlockHash);
        previousBlockHashSpan.on("click", function() {
            loadingImage.css("visibility", "visible");
            Api.search(block.previousBlockHash, function(data) {
                loadingImage.css("visibility", "hidden");

                const wasSuccess = data.wasSuccess;
                const errorMessage = data.errorMessage;
                const object = data.object;

                if (wasSuccess) {
                    UI.renderBlock(object);
                }
                else {
                   console.log(errorMessage);
                }
            });
        });
        $(".block-header .merkle-root .value", blockUi).text(block.merkleRoot);
        $(".block-header .timestamp .value", blockUi).text(block.timestamp.date);
        $(".block-header .nonce .value", blockUi).text(block.nonce);
        $(".block-header .reward .value", blockUi).text((block.reward / Constants.SATOSHIS_PER_BITCOIN).toFixed(4));
        $(".block-header .byte-count .value", blockUi).text(block.byteCount);

        const main = $("#main");
        main.empty();
        main.append(blockUi);
    }

    static renderTransaction(transaction) {
        console.log(transaction);
    }
}

$(document).ready(function() {
    const searchInput = $("#search");
    const loadingImage = $("#search-loading-image");
    searchInput.keypress(function(event) {
        const key = event.which;

        if (key == KeyCodes.ENTER) {
            loadingImage.css("visibility", "visible");
            Api.search(searchInput.val(), function(data) {
                loadingImage.css("visibility", "hidden");

                const wasSuccess = data.wasSuccess;
                const errorMessage = data.errorMessage;
                const objectType = data.objectType;
                const object = data.object;

                if (wasSuccess) {
                    if (objectType == Constants.BLOCK) {
                        UI.renderBlock(object);
                    }
                    else {
                        UI.renderTransaction(object);
                    }
                }
                else {
                   console.log(errorMessage);
                }
            });

            return false;  
        }
    });   
});

