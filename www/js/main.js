class Http {
    static get(url, data, callbackFunction) {
        const query = Object.keys(data).map(key => encodeURIComponent(key) + "=" + window.encodeURIComponent(data[key])).join("&");

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
        const searchInput = $("#search");
        searchInput.val(hash);

        const queryParams = new URLSearchParams(window.location.search);
        if (queryParams.get("search") != hash) {
            window.history.pushState({ hash: hash }, hash, "?search=" + window.encodeURIComponent(hash));
        }

        Http.get(Api.PREFIX + "search", { hash: hash }, callback);
    }
}
Api.PREFIX = "/api/v1/";

class Constants {
    static get BLOCK() { return "BLOCK"; }
    static get BLOCK_HEADER() { return "BLOCK_HEADER"; }
    static get TRANSACTION() { return "TRANSACTION"; }
    static get SATOSHIS_PER_BITCOIN() { return 100000000; }
}

class KeyCodes {
    static get ENTER() { return 13; }
}

class Ui {
    static inflateTransactionInput(transactionInput) {
        const templates = $("#templates");
        const transactionInputTemplate = $(".transaction-input", templates);
        const transactionInputUi = transactionInputTemplate.clone();

        $("div.label", transactionInputUi).on("click", function() {
            $("> div:not(:first-child)", transactionInputUi).toggle();
        });

        $(".address", transactionInputUi).text(transactionInput.address || "[CUSTOM SCRIPT]");
        $(".amount", transactionInputUi).text(transactionInput.amount);
        $(".transaction-hash .value", transactionInputUi).text(transactionInput.previousOutputTransactionHash);
        $(".transaction-output-index .value", transactionInputUi).text(transactionInput.previousOutputIndex);

        const sequenceNumber = transactionInput.sequenceNumber;
        $(".sequence-number .type .value", transactionInputUi).text(sequenceNumber.type);
        $(".sequence-number .type-value .value", transactionInputUi).text(sequenceNumber.value);
        $(".sequence-number .is-disabled .value", transactionInputUi).text((sequenceNumber.isDisabled ? "Yes" : "No"));
        $(".sequence-number .bytes .value", transactionInputUi).text(sequenceNumber.bytes);

        const operationsContainer = $(".unlocking-script .operations .value", transactionInputUi);
        const unlockingScript = transactionInput.unlockingScript;
        const operations = unlockingScript.operations;
        for (let i = 0; i < operations.length; i += 1) {
            const operation = operations[i];
            const scriptOperationUi = $(".script-operation", templates).clone();
            $(".value", scriptOperationUi).text(operation);
            operationsContainer.append(scriptOperationUi);
        }

        $(".unlocking-script .bytes .value", transactionInputUi).text(unlockingScript.bytes);

        return transactionInputUi;
    }

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
        previousBlockHashSpan.on("click", Ui._makeNavigateToBlockEvent(block.previousBlockHash));
        $(".block-header .merkle-root .value", blockUi).text(block.merkleRoot);
        $(".block-header .timestamp .value", blockUi).text(block.timestamp.date);
        $(".block-header .nonce .value", blockUi).text(block.nonce);
        $(".block-header .reward .value", blockUi).text((block.reward / Constants.SATOSHIS_PER_BITCOIN).toFixed(4));
        $(".block-header .byte-count .value", blockUi).text((block.byteCount || "-"));

        blockUi.hide();
        const main = $("#main");
        main.empty();
        main.append(blockUi);
        blockUi.fadeIn(500);
    }

    static _makeNavigateToBlockEvent(blockHash) {
        const loadingImage = $("#search-loading-image");

        return function() {
            loadingImage.css("visibility", "visible");
            Api.search(blockHash, function(data) {
                loadingImage.css("visibility", "hidden");

                const wasSuccess = data.wasSuccess;
                const errorMessage = data.errorMessage;
                const object = data.object;

                if (wasSuccess) {
                    Ui.renderBlock(object);
                }
                else {
                   console.log(errorMessage);
                }
            });
        };
    }

    static renderTransaction(transaction) {
        console.log(transaction);

        const loadingImage = $("#search-loading-image");

        const templates = $("#templates");
        const transactionTemplate = $(".transaction", templates);
        const transactionUi = transactionTemplate.clone();

        $(".hash .value", transactionUi).text(transaction.hash);
        $(".version .value", transactionUi).text(transaction.version);
        $(".byte-count .value", transactionUi).text(transaction.byteCount);
        $(".fee .value", transactionUi).text(transaction.fee);

        const blocks = transaction.blocks;
        for (let i = 0; i < blocks.length; i += 1) {
            const blockHash = blocks[i];
            const blockLink = $("<span class=\"fixed clickable\"></span>");
            blockLink.text(blockHash);
            blockLink.on("click", Ui._makeNavigateToBlockEvent(blockHash));
            $(".block-hashes .value", transactionUi).append(blockLink);
        }

        const lockTime = transaction.lockTime;
        $(".lock-time .type .value", transactionUi).text(lockTime.type);
        $(".lock-time .type-value .value", transactionUi).text(lockTime.date || lockTime.value);
        $(".lock-time .type-value .bytes", transactionUi).text(lockTime.bytes);

        const transactionInputs = transaction.inputs;
        for (let i = 0; i < transactionInputs.length; i += 1) {
            const transactionInput = transactionInputs[i];
            const transactionInputUi = Ui.inflateTransactionInput(transactionInput);
            $(".io .transaction-inputs", transactionUi).append(transactionInputUi);
        }

        transactionUi.hide();
        const main = $("#main");
        main.empty();
        main.append(transactionUi);
        transactionUi.fadeIn(500);
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

