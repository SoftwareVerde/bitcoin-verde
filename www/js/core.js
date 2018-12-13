class Http {
    static get(url, data, callbackFunction) {
        const query = Object.keys(data).map(key => window.encodeURIComponent(key) + "=" + window.encodeURIComponent(data[key])).join("&");

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
    static search(parameters, callback) {
        const defaultParameters = {
            hash: null
        };
        const apiParameters = $.extend({}, defaultParameters, parameters);

        const hash = apiParameters.hash;

        const searchInput = $("#search");
        searchInput.val(hash);

        const queryParams = new URLSearchParams(window.location.search);
        if (queryParams.get("search") != hash) {
            window.history.pushState({ hash: hash }, hash, "?search=" + window.encodeURIComponent(hash));
        }

        Http.get(Api.PREFIX + "search", apiParameters, callback);
    }

    static listBlockHeaders(parameters, callback) {
        const defaultParameters = {
            blockHeight: null,
            maxBlockCount: 25
        };
        const apiParameters = $.extend({}, defaultParameters, parameters);

        Http.get(Api.PREFIX + "blocks", apiParameters, callback);
    }

    static getStatus(parameters, callback) {
        const defaultParameters = { };
        const apiParameters = $.extend({}, defaultParameters, parameters);

        Http.get(Api.PREFIX + "status", apiParameters, callback);
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
            $("> div:not(:first-child)", transactionInputUi).slideToggle(250);
            return false;
        });

        $(".address", transactionInputUi).text(transactionInput.address || "[CUSTOM SCRIPT]");
        $(".amount", transactionInputUi).text((transactionInput.previousTransactionAmount || 0).toLocaleString());

        const transactionLink = $(".transaction-hash .value", transactionInputUi);
        transactionLink.text(transactionInput.previousOutputTransactionHash);
        transactionLink.on("click", Ui._makeNavigateToTransactionEvent(transactionInput.previousOutputTransactionHash));

        $(".transaction-output-index .value", transactionInputUi).text(transactionInput.previousOutputIndex);

        const sequenceNumber = transactionInput.sequenceNumber;
        $(".sequence-number .type .value", transactionInputUi).text(sequenceNumber.type);
        $(".sequence-number .type-value .value", transactionInputUi).text(sequenceNumber.value.toLocaleString());
        $(".sequence-number .is-disabled .value", transactionInputUi).text((sequenceNumber.isDisabled ? "Yes" : "No"));
        $(".sequence-number .bytes .value", transactionInputUi).text(sequenceNumber.bytes);

        const unlockingScript = transactionInput.unlockingScript;
        $(".unlocking-script .type .value", transactionInputUi).text(unlockingScript.scriptType);
        const operationsContainer = $(".unlocking-script .script .value", transactionInputUi);
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

    static inflateTransactionOutput(transactionOutput) {
        const templates = $("#templates");
        const transactionOutputTemplate = $(".transaction-output", templates);
        const transactionOutputUi = transactionOutputTemplate.clone();

        $("div.label", transactionOutputUi).on("click", function() {
            $("> div:not(:first-child)", transactionOutputUi).slideToggle(250);
            return false;
        });

        $(".address", transactionOutputUi).text(transactionOutput.address || "[CUSTOM SCRIPT]");
        $(".amount", transactionOutputUi).text((transactionOutput.amount || 0).toLocaleString());

        const lockingScript = transactionOutput.lockingScript;
        $(".locking-script .type .value", transactionOutputUi).text(lockingScript.scriptType);
        const operationsContainer = $(".locking-script .script .value", transactionOutputUi);
        const operations = lockingScript.operations;
        for (let i = 0; i < operations.length; i += 1) {
            const operation = operations[i];
            const scriptOperationUi = $(".script-operation", templates).clone();
            $(".value", scriptOperationUi).text(operation);
            operationsContainer.append(scriptOperationUi);
        }

        $(".locking-script .bytes .value", transactionOutputUi).text(lockingScript.bytes);

        return transactionOutputUi;
    }

    static renderBlock(block) {
        const loadingImage = $("#search-loading-image");

        const blockUi = Ui.inflateBlock(block);

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
            Api.search({ hash: blockHash }, function(data) {
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

            return false;
        };
    }

    static _makeNavigateToTransactionEvent(transactionHash) {
        const loadingImage = $("#search-loading-image");

        return function() {
            loadingImage.css("visibility", "visible");
            Api.search({ hash: transactionHash }, function(data) {
                loadingImage.css("visibility", "hidden");

                const wasSuccess = data.wasSuccess;
                const errorMessage = data.errorMessage;
                const object = data.object;

                if (wasSuccess) {
                    Ui.renderTransaction(object);
                }
                else {
                   console.log(errorMessage);
                }
            });

            return false;
        };
    }

    static renderTransaction(transaction) {
        const transactionUi = Ui.inflateTransaction(transaction);
        transactionUi.hide();
        const main = $("#main");
        main.empty();
        main.append(transactionUi);
        transactionUi.fadeIn(500);
    }

    static inflateBlock(block) {
        const templates = $("#templates");
        const blockTemplate = $(".block", templates);
        const blockUi = blockTemplate.clone();

        $(".block-header .height .value", blockUi).text(block.height.toLocaleString());
        $(".block-header .hash .value", blockUi).text(block.hash);
        $(".block-header .difficulty .mask .value", blockUi).text(block.difficulty.mask);
        $(".block-header .difficulty .ratio .value", blockUi).text(block.difficulty.ratio.toLocaleString());
        const previousBlockHashSpan = $(".block-header .previous-block-hash .value", blockUi);
        previousBlockHashSpan.text(block.previousBlockHash);
        previousBlockHashSpan.on("click", Ui._makeNavigateToBlockEvent(block.previousBlockHash));
        $(".block-header .merkle-root .value", blockUi).text(block.merkleRoot);
        $(".block-header .timestamp .value", blockUi).text(block.timestamp.date);
        $(".block-header .nonce .value", blockUi).text(block.nonce.toLocaleString());
        $(".block-header .reward .value", blockUi).text((block.reward / Constants.SATOSHIS_PER_BITCOIN).toLocaleString());
        $(".block-header .byte-count .value", blockUi).text((block.byteCount || "-").toLocaleString());
        $(".block-header .transaction-count .value", blockUi).text((block.transactionCount || "-").toLocaleString());

        const transactions = (block.transactions || []);
        for (let i = 0; i < transactions.length; i += 1) {
            const transaction = transactions[i];
            const transactionUi = Ui.inflateTransaction(transaction);
            $(".transactions", blockUi).append(transactionUi);
        }

        return blockUi;
    }

    static inflateTransaction(transaction) {
        const templates = $("#templates");
        const transactionTemplate = $(".transaction", templates);
        const transactionUi = transactionTemplate.clone();

        transactionUi.on("click", function() {
            const elements = $(".hash label, .version, .byte-count, .fee, .block-hashes, .lock-time, .version", transactionUi);
            elements.each(function() {
                const element = $(this);
                elements.animationCompleteCount = 0;
                if (element.css("display") == "none") {
                    element.slideDown(function() {
                        element.css("visibility", "visible");
                        elements.animationCompleteCount += 1;

                        if (elements.animationCompleteCount >= elements.length) {
                            transactionUi.toggleClass("collapsed");
                        }
                    });
                }
                else {
                    element.slideUp(function() {
                        element.css("visibility", "hidden");
                        elements.animationCompleteCount += 1;

                        if (elements.animationCompleteCount >= elements.length) {
                            transactionUi.toggleClass("collapsed");
                        }
                    });
                }
            });
            // $(".hash label, .version, .byte-count, .fee, .block-hashes, .lock-time, .version", transactionUi).slideToggle(500);
            return false;
        });
        $(".hash label, .version, .byte-count, .fee, .block-hashes, .lock-time, .version", transactionUi).css("display", "none");

        const transactionHashElement = $(".hash .value", transactionUi);
        transactionHashElement.text(transaction.hash);
        transactionHashElement.on("click", Ui._makeNavigateToTransactionEvent(transaction.hash));

        $(".version .value", transactionUi).text(transaction.version);
        $(".byte-count .value", transactionUi).text((transaction.byteCount || "-").toLocaleString());
        $(".fee .value", transactionUi).text((transaction.fee != null ? transaction.fee : "-").toLocaleString());

        const blocks = (transaction.blocks || []);
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

        const transactionOutputs = transaction.outputs;
        for (let i = 0; i < transactionOutputs.length; i += 1) {
            const transactionOutput = transactionOutputs[i];
            const transactionOutputUi = Ui.inflateTransactionOutput(transactionOutput);
            $(".io .transaction-outputs", transactionUi).append(transactionOutputUi);
        }

        return transactionUi;
    }
}

