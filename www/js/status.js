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
            const status = (data.status || "OFFLINE");
            const statistics = data.statistics;
            const serverLoad = data.serverLoad;

            $(".status-value").text(status);
            $(".status-value").css("background-color", (status == "ONLINE" ? "#1AB326" : "#B31A26"));

            if (wasSuccess) {
                $(".block-header-height-value").text(statistics.blockHeaderHeight);
                $(".block-header-date-value").text(statistics.blockHeaderDate);
                $(".block-height-value").text(statistics.blockHeight);
                $(".block-date-value").text(statistics.blockDate);

                $(".block-headers-per-second").text(statistics.blockHeadersPerSecond);
                $(".blocks-per-second").text(statistics.blocksPerSecond);
                $(".transactions-per-second").text(statistics.transactionsPerSecond);

                { // Server Load
                    const threadPoolActiveThreadCount = window.parseFloat(serverLoad.threadPoolActiveThreadCount);
                    const threadPoolMaxThreadCount = window.parseFloat(serverLoad.threadPoolMaxThreadCount);
                    const threadPoolQueueCount = window.parseFloat(serverLoad.threadPoolQueueCount);
                    const percentServerLoad = ((100.0 * threadPoolActiveThreadCount + threadPoolQueueCount) / threadPoolMaxThreadCount).toFixed(2);
                    $(".server-load-bar .server-load-fill").css("width", (percentServerLoad > 100 ? 100 : percentServerLoad) + "%");
                    $(".server-load-bar .server-load-text").text(percentServerLoad + "%");
                }

                { // Server Memory
                    const jvmMemoryUsageByteCount = window.parseFloat(serverLoad.jvmMemoryUsageByteCount);
                    const jvmMemoryMaxByteCount = window.parseFloat(serverLoad.jvmMemoryMaxByteCount);
                    const percentMemoryUsage = (100.0 * (jvmMemoryUsageByteCount / jvmMemoryMaxByteCount)).toFixed(2);
                    $(".server-memory-bar .server-memory-fill").css("width", (percentMemoryUsage > 100 ? 100 : percentMemoryUsage) + "%");
                    $(".server-memory-bar .server-memory-text").text(percentMemoryUsage + "%");
                }

                { // Sync Progress
                    const blockHeaderHeight = window.parseFloat(statistics.blockHeaderHeight);
                    const blockHeight = window.parseFloat(statistics.blockHeight);
                    const percentComplete = (100.0 * (blockHeight / blockHeaderHeight)).toFixed(2);
                    $(".progress-done").css("width", percentComplete + "%");
                    $(".percent-done-text").text(percentComplete + "%");
                }
            }
            else {
               console.log(errorMessage);
            }
        });
    }

    static updateNodes() {
        const loadingImage = $("#search-loading-image");

        loadingImage.css("visibility", "visible");

        const parameters = { };
        Api.getNodes(parameters, function(data) {
            loadingImage.css("visibility", "hidden");

            const wasSuccess = data.wasSuccess;
            const errorMessage = data.errorMessage;
            const nodes = data.nodes;

            if (wasSuccess) {
                const nodesContainer = $(".status-container .nodes");
                nodesContainer.empty();

                nodes.sort(function(node0, node1) {
                    return node0.id - node1.id;
                });

                for (const i in nodes) {
                    const node = nodes[i];
                    const nodeElement = $("#templates .node").clone();
                    $(".id", nodeElement).text(node.id);
                    $(".user-agent", nodeElement).text(node.userAgent);
                    $(".host", nodeElement).text(node.host);
                    $(".port", nodeElement).text(node.port);

                    $(".network-offset", nodeElement).text(node.networkOffset);
                    $(".handshake-complete", nodeElement).text(node.handshakeIsComplete);
                    $(".initialization-timestamp", nodeElement).text(node.initializationTimestamp);
                    $(".last-message-timestamp", nodeElement).text(node.lastMessageReceivedTimestamp);

                    $(".local-host", nodeElement).text(node.localHost);
                    $(".local-port", nodeElement).text(node.localPort);

                    const featuresContainer = $(".features", nodeElement);
                    for (const feature in node.features) {
                        const value = node.features[feature];

                        let featureElement = null;
                        if ( (value == "0") || (value == "1") ) {
                            featureElement = $("<div class=\"feature\"><label class=\"" + (value == "0" ? "disabled" : "") + "\">" + feature + "</label></div>");
                        }
                        else {
                            featureElement = $("<div class=\"feature\"><label>" + feature + "</label><span>" + value + "</span></div>");
                        }

                        $(featuresContainer).append(featureElement);
                    }

                    nodesContainer.append(nodeElement);
                }
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

    StatusUi.updateNodes();
    window.setInterval(function() {
        StatusUi.updateNodes();
    }, 20000);
});


