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

    Api.getPoolHashRate({ }, function(data) {
        loadingImage.css("visibility", "hidden");

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
