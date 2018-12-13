class StatusUi {
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
            StatusUi.search(hash);
            return false;  
        }
    });   

    loadingImage.css("visibility", "visible");

    const parameters = {
        blockHeight: null,
        maxBlockCount: 25
    };

    Api.getStatus(parameters, function(data) {
        loadingImage.css("visibility", "hidden");

        const wasSuccess = data.wasSuccess;
        const errorMessage = data.errorMessage;
        const status = data.status;
        const statistics = data.statistics;

        if (wasSuccess) {
            $(".status-value").text(status);
            $(".block-header-height-value").text(statistics.blockHeaderHeight);
            $(".block-header-date-value").text(statistics.blockHeaderDate);
            $(".block-height-value").text(statistics.blockHeight);
            $(".block-date-value").text(statistics.blockDate);

            $(".block-headers-per-second").text(statistics.blockHeadersPerSecond);
            $(".blocks-per-second").text(statistics.blocksPerSecond);
            $(".transactions-per-second").text(statistics.transactionsPerSecond);
        }
        else {
           console.log(errorMessage);
        }
    });

});


