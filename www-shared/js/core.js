class Http {
    static _jsonToQueryString(jsonData) {
        return Object.keys((jsonData ? jsonData : { })).map(key => window.encodeURIComponent(key) + "=" + window.encodeURIComponent(jsonData[key])).join("&");
    }

    static _getPostParameters(parameters) {
        const postParameters = {
            url: parameters[0],
            getData: null,
            postData: null,
            callbackFunction: null
        };

        if (parameters.length == 4) {                    // url, getData, postData, callbackFunction
            postParameters.getData = parameters[1];
            postParameters.postData = parameters[2];
            postParameters.callbackFunction = parameters[3];
        }
        else if (parameters.length == 2) {               // url, postData
            postParameters.postData = parameters[1];
        }
        else {
            if (typeof parameters[2] == "function") {    // url, postData, callbackFunction
                postParameters.postData = parameters[1];
                postParameters.callbackFunction = parameters[2];
            }
            else {                                      // url, getData, postData
                postParameters.getData = parameters[1];
                postParameters.postData = parameters[2];
            }
        }

        return postParameters;
    }

    static _send(method, url, getQueryString, rawPostData, callbackFunction) {
        const request = new Request(
            url + (getQueryString? ("?" + getQueryString) : ""),
            {
                method:         method,
                credentials:    "include",
                body:           (rawPostData ? rawPostData : null)
            }
        );

        window.fetch(request, { credentials: "same-origin" })
            .then(function(response) {
                const contentType = (response.headers.get("content-type") || "");
                if (contentType.includes("application/json")) {
                    return response.json();
                }
                return { wasSuccess: false, errorMessage: "Invalid response." };
            })
            .then(function(json) {
                if (typeof callbackFunction == "function") {
                    callbackFunction(json);
                }
            });
    }

    static get(url, getData, callbackFunction) {
        Http._send("GET", url, Http._jsonToQueryString(getData), null, callbackFunction);
    }

    static post() { // Params: url, getData, postData, callbackFunction
        const postParameters = Http._getPostParameters(arguments);
        Http._send("POST", postParameters.url, Http._jsonToQueryString(postParameters.getData), Http._jsonToQueryString(postParameters.postData), postParameters.callbackFunction);
    }

    static postJson() { // Params: url, getData, postData, callbackFunction
        const postParameters = Http._getPostParameters(arguments);
        Http._send("POST", postParameters.url, Http._jsonToQueryString(postParameters.getData), postParameters.postData, postParameters.callbackFunction);
    }
}

class Api {
    static search(parameters, callback) {
        const defaultParameters = {
            query: null
        };
        const apiParameters = $.extend({ }, defaultParameters, parameters);

        const query = apiParameters.query;

        const searchInput = $("#search");
        searchInput.val(query);

        const queryParams = new URLSearchParams(window.location.search);
        if (queryParams.get("search") != query) {
            window.history.pushState({ query: query}, query, "?search=" + window.encodeURIComponent(query));
        }

        Http.get(Api.PREFIX + "search", apiParameters, callback);
    }

    static listBlockHeaders(parameters, callback) {
        const defaultParameters = {
            blockHeight: null,
            maxBlockCount: 25
        };
        const apiParameters = $.extend({ }, defaultParameters, parameters);

        Http.get(Api.PREFIX + "blocks", apiParameters, callback);
    }

    static getStatus(parameters, callback) {
        const defaultParameters = { };
        const apiParameters = $.extend({ }, defaultParameters, parameters);

        Http.get(Api.PREFIX + "status", apiParameters, callback);
    }

    static getNodes(parameters, callback) {
        const defaultParameters = { };
        const apiParameters = $.extend({ }, defaultParameters, parameters);

        Http.get(Api.PREFIX + "nodes", apiParameters, callback);
    }

    static getBlockchainMetadata(parameters, callback) {
        const defaultParameters = { };
        const apiParameters = $.extend({ }, defaultParameters, parameters);

        Http.get(Api.PREFIX + "blockchain", apiParameters, callback);
    }

    static postTransaction(parameters, callback) {
        const defaultParameters = { transactionData: null };
        const apiParameters = $.extend({ }, defaultParameters, parameters);

        Http.post(Api.PREFIX + "transactions", apiParameters, callback);
    }

    static getPrototypeBlock(parameters, callback) {
        const defaultParameters = { };
        const apiParameters = $.extend({ }, defaultParameters, parameters);

        Http.get(Api.PREFIX + "pool/prototype-block", apiParameters, callback);
    }

    static getPoolHashRate(parameters, callback) {
        const defaultParameters = { };
        const apiParameters = $.extend({ }, defaultParameters, parameters);

        Http.get(Api.PREFIX + "pool/hash-rate", apiParameters, callback);
    }
}
Api.PREFIX = "/api/v1/";

class Constants {
    static get BLOCK() { return "BLOCK"; }
    static get BLOCK_HEADER() { return "BLOCK_HEADER"; }
    static get ADDRESS() { return "ADDRESS"; }
    static get TRANSACTION() { return "TRANSACTION"; }
    static get SATOSHIS_PER_BITCOIN() { return 100000000; }
}

class KeyCodes {
    static get ENTER() { return 13; }
}

class Ui {
    static makeHashCopyable(element) {
        const copyButton = $("<span class=\"copy\"></span>");
        copyButton.on("click", function() {
            const parent = $(this).parent();
            window.Clipboard.copy(parent.data("hash-value") || parent.text());
            return false;
        });
        element.append(copyButton);
        element.toggleClass("copyable", true);
    }

    static inflateTransactionInput(transactionInput) {
        const templates = $("#templates");
        const transactionInputTemplate = $("> .transaction-input", templates);
        const transactionInputUi = transactionInputTemplate.clone();

        $("div.label", transactionInputUi).on("click", function() {
            $("> div:not(:first-child)", transactionInputUi).slideToggle(250, function() {
                window.HashResizer(transactionInputUi);
            });
            return false;
        });

        $(".address", transactionInputUi).text(transactionInput.address || "[CUSTOM SCRIPT]");
        if (transactionInput.address) {
            Ui.makeHashCopyable($(".address", transactionInputUi));
        }
        $(".amount", transactionInputUi).text((transactionInput.previousTransactionAmount || 0).toLocaleString());

        const transactionLink = $(".transaction-hash .value", transactionInputUi);
        transactionLink.text(transactionInput.previousOutputTransactionHash);
        Ui.makeHashCopyable(transactionLink);
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
            const scriptOperationUi = $("> .script-operation", templates).clone();
            $(".value", scriptOperationUi).text(operation);
            operationsContainer.append(scriptOperationUi);
        }

        $(".unlocking-script .bytes .value", transactionInputUi).text(unlockingScript.bytes);

        return transactionInputUi;
    }

    static inflateTransactionOutput(transactionOutput) {
        const templates = $("#templates");
        const transactionOutputTemplate = $("> .transaction-output", templates);
        const transactionOutputUi = transactionOutputTemplate.clone();

        $("div.label", transactionOutputUi).on("click", function() {
            $("> div:not(:first-child)", transactionOutputUi).slideToggle(250, function() {
                window.HashResizer(transactionOutputUi);
            });
            return false;
        });

        $(".address", transactionOutputUi).text(transactionOutput.address || "[CUSTOM SCRIPT]");
        if (transactionOutput.address) {
            Ui.makeHashCopyable($(".address", transactionOutputUi));
        }
        $(".amount", transactionOutputUi).text((transactionOutput.amount || 0).toLocaleString());

        const lockingScript = transactionOutput.lockingScript;
        $(".locking-script .type .value", transactionOutputUi).text(lockingScript.scriptType);
        const operationsContainer = $(".locking-script .script .value", transactionOutputUi);
        const operations = lockingScript.operations;
        for (let i = 0; i < operations.length; i += 1) {
            const operation = operations[i];
            const scriptOperationUi = $("> .script-operation", templates).clone();
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
        blockUi.fadeIn(500, function() {
            window.HashResizer(blockUi);
        });
    }

    static highlightAddress(address, transactionUi) {
        $("label.address", transactionUi).each(function() {
            const shouldHighlight = (address == $(this).text());
            $(this).toggleClass("highlight", shouldHighlight);
            $(this).parent().toggleClass("highlight", shouldHighlight);
        });
    }

    static renderAddress(addressObject) {
        const main = $("#main");
        main.empty();

        const addressUi = Ui.inflateAddress(addressObject);
        main.append(addressUi);
        window.HashResizer(addressUi);
    }

    static _makeNavigateToBlockEvent(blockHash) {
        const loadingImage = $("#search-loading-image");

        return function() {
            loadingImage.css("visibility", "visible");
            Api.search({ query: blockHash }, function(data) {
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
            Api.search({ query: transactionHash }, function(data) {
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

    static _getLoadingElement() {
        if ($("#loading").length == 0) {
            const loadingElement = $("<div id=\"loading\"></div>");
            loadingElement.css({ position: "fixed", left: "0", right: "0", bottom: "0", height: "0.5em", background: "#202020" });

            const progress = $("<div></div>");
            progress.css({ height: "100%", background: "#1AB326" });

            loadingElement.append(progress);
            $("body").append(loadingElement);
        }
        return $("#loading");
    }

    static renderTransaction(transaction) {
        const transactionUi = Ui.inflateTransaction(transaction);
        transactionUi.hide();
        const main = $("#main");
        main.empty();
        main.append(transactionUi);
        transactionUi.fadeIn(500, function() {
            window.HashResizer(transactionUi);
        });
    }

    static inflateBlock(block) {
        const templates = $("#templates");
        const blockTemplate = $("> .block", templates);
        const blockUi = blockTemplate.clone();

        $(".block-header .height .value", blockUi).text((block.height || "0").toLocaleString());
        $(".block-header .hash .value", blockUi).text(block.hash);
        Ui.makeHashCopyable($(".block-header .hash .value", blockUi).text(block.hash));
        $(".block-header .difficulty .mask .value", blockUi).text(block.difficulty.mask);
        $(".block-header .difficulty .ratio .value", blockUi).text(block.difficulty.ratio.toLocaleString());
        const previousBlockHashElement = $(".block-header .previous-block-hash .value", blockUi);
        previousBlockHashElement.text(block.previousBlockHash);
        Ui.makeHashCopyable(previousBlockHashElement);
        previousBlockHashElement.on("click", Ui._makeNavigateToBlockEvent(block.previousBlockHash));
        $(".block-header .merkle-root .value", blockUi).text(block.merkleRoot);
        $(".block-header .timestamp .value", blockUi).text(DateUtil.formatDateIso(block.timestamp.value));
        $(".block-header .nonce .value", blockUi).text(block.nonce.toLocaleString());
        $(".block-header .reward .value", blockUi).text((block.reward ? (block.reward / Constants.SATOSHIS_PER_BITCOIN) : "-").toLocaleString());
        $(".block-header .byte-count .value", blockUi).text((block.byteCount || "-").toLocaleString());
        $(".block-header .transaction-count .value", blockUi).text((block.transactionCount || (block.transactions ? block.transactions.length : null ) || "-").toLocaleString());

        const loadingElement = Ui._getLoadingElement();
        const appendTransaction = function(i, transactions) {
            if (i >= transactions.length) {
                loadingElement.remove();
                return;
            }

            const transaction = transactions[i];
            const transactionUi = Ui.inflateTransaction(transaction);
            $(".transactions", blockUi).append(transactionUi);
            $("div", loadingElement).css({ width: (((i*100) / transactions.length) + "%") });
            window.setTimeout(appendTransaction, 0, (i+1), transactions);
        };

        const transactions = (block.transactions || []);
        appendTransaction(0, transactions);

        return blockUi;
    }

    static inflateTransaction(transaction) {
        const templates = $("#templates");
        const transactionTemplate = $("> .transaction", templates);
        const transactionUi = transactionTemplate.clone();

        $("> div > div", transactionUi).on("click", function() {
            if ($(transactionUi).is(".collapsed") && $(this).is(".hash")) { return true; }
            if ($(this).is(".io")) { return true; }
            return false;
        });

        $(".transaction-inputs, .transaction-outputs", transactionUi).on("click", function() {
            return false;
        });

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

                            window.HashResizer(transactionUi);
                        }
                    });
                }
                else {
                    element.slideUp(function() {
                        element.css("visibility", "hidden");
                        elements.animationCompleteCount += 1;

                        if (elements.animationCompleteCount >= elements.length) {
                            transactionUi.toggleClass("collapsed");

                            window.HashResizer(transactionUi);
                        }
                    });
                }
            });

            return false;
        });
        $(".hash label, .version, .byte-count, .fee, .block-hashes, .lock-time, .version, .slp", transactionUi).css("display", "none");

        const transactionHashElement = $(".hash .value", transactionUi);
        transactionHashElement.text(transaction.hash);
        Ui.makeHashCopyable(transactionHashElement);
        transactionHashElement.on("click", Ui._makeNavigateToTransactionEvent(transaction.hash));

        $(".version .value", transactionUi).text(transaction.version);
        $(".byte-count .value", transactionUi).text((transaction.byteCount || "-").toLocaleString());
        $(".fee .value", transactionUi).text((transaction.fee != null ? transaction.fee : "-").toLocaleString());

        if (transaction.slp != null) {
            const slpAttribute = $(".slp .value", transactionUi);
            slpAttribute.text(transaction.slp.tokenName);
            slpAttribute.toggleClass("invalid", (! transaction.slp.isValid));
        }
        else {
            $(".slp .value", transactionUi).remove();
        }

        const blocks = (transaction.blocks || []);
        for (let i = 0; i < blocks.length; i += 1) {
            const blockHash = blocks[i];
            const blockLink = $("<div class=\"value fixed clickable\"></div>");
            blockLink.text(blockHash);
            Ui.makeHashCopyable(blockLink);
            blockLink.on("click", Ui._makeNavigateToBlockEvent(blockHash));
            $(".block-hashes .values", transactionUi).append(blockLink);
        }

        const lockTime = (transaction.lockTime || { type:"", value: "", bytes: "" });
        if (lockTime.type == "BLOCK_HEIGHT") {
            $(".lock-time .value", transactionUi).text("Locked until Block #" + lockTime.value);
        }
        else {
            $(".lock-time .value", transactionUi).text("Locked until Block #" + lockTime.date);
        }
        $(".lock-time .type-value .bytes", transactionUi).text(lockTime.bytes);

        const transactionInputs = (transaction.inputs || [ ]);
        for (let i = 0; i < transactionInputs.length; i += 1) {
            const transactionInput = transactionInputs[i];
            const transactionInputUi = Ui.inflateTransactionInput(transactionInput);
            $(".io .transaction-inputs", transactionUi).append(transactionInputUi);
        }

        const transactionOutputs = (transaction.outputs || [ ]);
        for (let i = 0; i < transactionOutputs.length; i += 1) {
            const transactionOutput = transactionOutputs[i];
            const transactionOutputUi = Ui.inflateTransactionOutput(transactionOutput);
            $(".io .transaction-outputs", transactionUi).append(transactionOutputUi);
        }

        return transactionUi;
    }

    static inflateAddress(addressObject) {
        const templates = $("#templates");
        const addressTemplate = $("> .address", templates);
        const addressUi = addressTemplate.clone();

        const addressString = (addressObject.base58CheckEncoded || "");
        const addressBalance = (addressObject.balance || 0);
        const addressTransactions = addressObject.transactions;

        const qrCodeElement = window.ninja.qrCode.createCanvas(addressString, 4);

        $(".address", addressUi).text(addressString);
        $(".address-balance", addressUi).text(addressBalance.toLocaleString());
        $(".qr-code", addressUi).append(qrCodeElement);

        const addressTransactionsContainer = $(".address-transactions", addressUi);

        const loadingElement = Ui._getLoadingElement();
        const appendTransaction = function(i, transactions) {
            if (i >= transactions.length) {
                loadingElement.remove();
                return;
            }

            const transaction = transactions[i];
            const transactionUi = Ui.inflateTransaction(transaction);
            Ui.highlightAddress(addressString, transactionUi);
            addressTransactionsContainer.append(transactionUi);

            $("div", loadingElement).css({ width: (((i*100) / transactions.length) + "%") });
            window.setTimeout(appendTransaction, 0, (i+1), transactions);
        };

        appendTransaction(0, addressTransactions);

        return addressUi;
    }
}

class DateUtil {
    static getTimeZoneAbbreviation() {
        // Derived From: https://stackoverflow.com/questions/1954397/detect-timezone-abbreviation-using-javascript#38708623
        try {
            // Chrome, Firefox
            return (/.*\s(.+)/.exec((new Date()).toLocaleDateString(navigator.language, { timeZoneName:'short' }))[1]);
        }
        catch(exception) {
            // IE
            return ((new Date()).toTimeString().match(new RegExp("[A-Z](?!.*[\(])","g")).join(''));
        }
    }

    static padLeft(number) {
        return ((number < 10 ? "0" : "") + number);
    }

    static formatDateIso(date) {
        if ( (typeof date == "number") || (typeof date == "string") ) {
            const newDate = new Date(0);
            newDate.setUTCSeconds(date);
            date = newDate;
        }
        return (date.getFullYear() + "-" + DateUtil.padLeft(date.getMonth() + 1) + "-" + DateUtil.padLeft(date.getDate()) + " " + DateUtil.padLeft(date.getHours()) + ":" + DateUtil.padLeft(date.getMinutes()) + ":" + DateUtil.padLeft(date.getSeconds()) + " " + DateUtil.getTimeZoneAbbreviation());
    }
}

