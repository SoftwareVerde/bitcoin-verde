class StatusUi {
    static search(objectHash) {
        document.location.href = "/?search=" + window.encodeURIComponent(objectHash);
    }

    static updateStatus() {
        const loadingImage = $("#search-loading-image");

        loadingImage.css("visibility", "visible");

        const parameters = { };
        Api.getStatus(parameters, function(data) {
            loadingImage.css("visibility", "hidden");

            const wasSuccess = data.wasSuccess;
            const errorMessage = data.errorMessage;
            const status = data.status;
            const statistics = data.statistics;

            if (wasSuccess) {
                $(".status-value").text(status);
                $(".status-value").css("background-color", (status == "ONLINE" ? "#1AB326" : "#B31A26"));

                $(".block-header-height-value").text(statistics.blockHeaderHeight);
                $(".block-header-date-value").text(statistics.blockHeaderDate);
                $(".block-height-value").text(statistics.blockHeight);
                $(".block-date-value").text(statistics.blockDate);

                $(".block-headers-per-second").text(statistics.blockHeadersPerSecond);
                $(".blocks-per-second").text(statistics.blocksPerSecond);
                $(".transactions-per-second").text(statistics.transactionsPerSecond);

                const percentComplete = (Math.round(100.0 * (window.parseFloat(statistics.blockHeight) / window.parseFloat(statistics.blockHeaderHeight))) / 100.0) * 100;
                $(".progress-done").css("width", percentComplete + "%");
                $(".percent-done-text").text(percentComplete + "%");
            }
            else {
               console.log(errorMessage);
            }
        });
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

    StatusUi.updateStatus();
    window.setInterval(function() {
        StatusUi.updateStatus();
    }, 5000);
});


