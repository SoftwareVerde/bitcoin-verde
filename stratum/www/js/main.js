class Data {
    static paginateBlockTransactions(pageNumber, transactionsPerPage) {
        if (Data.prototypeBlock == null) { return []; }

        const transactions = Data.prototypeBlock.transactions;
        const transactionCount = transactions.length;
        const pageCount = Math.floor((transactionCount + transactionsPerPage - 1) / transactionsPerPage);

        const page = [];
        for (let i = 0; i < transactionsPerPage; ++i) {
            const index = (pageNumber * transactionsPerPage + i);
            if (index >= transactionCount) { break; }

            const transaction = transactions[index];
            page.push(transaction);
        }
        return page;
    }
}
Data.prototypeBlock = null;

Ui._superRenderBlock = Ui.renderBlock;
Ui.renderBlock = function(block) {
    const allTransactions = block.transactions;
    block.transactions = Data.paginateBlockTransactions(0, 32);

    Ui._superRenderBlock(block);
    block.transactions = allTransactions;

    const main = $("#main");
    const blockUi = main.first();

    const transactionCount = allTransactions.length;
    $(".block-header .transaction-count .value", blockUi).text((transactionCount || "-").toLocaleString());
};

Api._superGetBlockTransactions = Api.getBlockTransactions;
Api.getBlockTransactions = function(blockHash, parameters, callback) {
    if (Data.prototypeBlock == null) { return; }

    const transactionCount = Data.prototypeBlock.transactions.length;
    const pageNumber = parseInt(parameters.pageNumber);
    const pageSize = parseInt(parameters.pageSize);

    const transactions = Data.paginateBlockTransactions(pageNumber, pageSize);

    const response = {"transactions": transactions};
    callback(response);
};

$(document).ready(function() {
    Api.getPrototypeBlock({ }, function(data) {
        const wasSuccess = data.wasSuccess;
        const errorMessage = data.errorMessage;
        const block = data.block;
        Data.prototypeBlock = block;

        if (wasSuccess) {
            Ui.renderBlock(block);
            $("#main .block .transaction:first-child").trigger("click");
        }
        else {
           console.log(errorMessage);
        }
    });

    Api.getPoolHashRate({ }, function(data) {
        const wasSuccess = data.wasSuccess;
        const errorMessage = data.errorMessage;
        const hashesPerSecond = parseInt(data.hashesPerSecond);

        const prefixes = ["kilo", "mega", "giga", "tera", "peta", "exa"];

        if (wasSuccess) {
            let prefix = "";
            let factor = 1;
            if (hashesPerSecond < 1000000) {
                prefix = "kilo";
                factor = 1000;
            }
            else if (hashesPerSecond < 1000000000) {
                prefix = "mega";
                factor = 1000000;
            }
            else if (hashesPerSecond < 1000000000000) {
                prefix = "giga";
                factor = 1000000000;
            }
            else if (hashesPerSecond < 1000000000000000) {
                prefix = "tera";
                factor = 1000000000000;
            }
            else if (hashesPerSecond < 1000000000000000000) {
                prefix = "peta";
                factor = 1000000000000000;
            }
            else {
                prefix = "exa";
                factor = 1000000000000000000;
            }

            const hashRateElement = $("#pool-hash-rate");
            for (let i = 0; i < prefixes.length; i += 1) {
                hashRateElement.toggleClass(prefixes[i], false);
            }
            hashRateElement.toggleClass(prefix, true);
            hashRateElement.text((hashesPerSecond / factor).toFixed(2));

        }
        else {
           console.log(errorMessage);
        }
    });
});
