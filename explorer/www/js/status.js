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
            const utxoCacheStatus = data.utxoCacheStatus;
            const statistics = data.statistics;
            const serverLoad = data.serverLoad;
            const serviceStatuses = data.serviceStatuses;

            { // Node Status
                $(".status-value").text(status);
                let statusColor = "#B31A26";
                if (status == "ONLINE") {
                    statusColor = "#1AB326";
                }
                else if (status == "SYNCHRONIZING") {
                    statusColor = "#F99300";
                }
                $(".status-value").css("background-color", statusColor);
            }

            if (wasSuccess) {
                $(".block-header-height-value").text(statistics.blockHeaderHeight);
                $(".block-header-date-value").text(DateUtil.formatDateIso(statistics.blockHeaderTimestamp));
                $(".block-height-value").text(statistics.blockHeight);
                $(".block-date-value").text(DateUtil.formatDateIso(statistics.blockTimestamp));

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

                { // UTXO Cache
                    // utxoCacheStatus.utxoCacheCount, utxoCacheStatus.maxUtxoCacheCount, utxoCacheStatus.uncommittedUtxoCount, utxoCacheStatus.committedUtxoBlockHeight
                    const utxoCacheCount = window.parseFloat(utxoCacheStatus.utxoCacheCount);
                    const maxUtxoCacheCount = window.parseFloat(utxoCacheStatus.maxUtxoCacheCount);
                    const percentCached = (100.0 * (utxoCacheCount / maxUtxoCacheCount)).toFixed(2);
                    $(".utxo-cache-bar .utxo-cache-fill").css("width", (percentCached > 100 ? 100 : percentCached) + "%");
                    $(".utxo-cache-bar .utxo-cache-text").text(percentCached + "%");
                }

                { // Sync Progress
                    const blockHeaderHeight = window.parseFloat(statistics.blockHeaderHeight);
                    const blockHeight = window.parseFloat(statistics.blockHeight);
                    const percentComplete = (100.0 * (blockHeight / blockHeaderHeight)).toFixed(2);
                    $(".progress-done").css("width", percentComplete + "%");
                    $(".percent-done-text").text(percentComplete + "%");
                }

                { // Service Statuses
                    const serviceStatusContainer = $(".service-statuses");
                    serviceStatusContainer.empty();
                    let serviceNames = [];
                    for (const serviceName in serviceStatuses) {
                        serviceNames.push(serviceName);
                    }
                    serviceNames.sort();
                    for (const i in serviceNames) {
                        const serviceName = serviceNames[i];
                        const serviceStatus = serviceStatuses[serviceName];
                        const element = $("<div></div>");
                        element.text(serviceName);
                        element.toggleClass("service-active", (serviceStatus == "ACTIVE"));
                        serviceStatusContainer.append(element);
                    }
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

                    if (node.isPreferred) {
                        $(nodeElement).toggleClass("preferred");
                        $(".user-agent", nodeElement).attr("title", "Preferred Node");
                    }

                    $(".id", nodeElement).text(node.id);
                    $(".user-agent", nodeElement).text(node.userAgent);
                    $(".host", nodeElement).text(node.host);
                    $(".port", nodeElement).text(" " + node.port); // Whitespace prevents the host/port from being concatenated upon select.
                    $(".ping", nodeElement).text(node.ping);

                    $(".block-height", nodeElement).text(node.blockHeight);
                    $(".network-offset", nodeElement).text(node.networkOffset);
                    $(".handshake-complete", nodeElement).text(node.handshakeIsComplete);
                    $(".initialization-timestamp", nodeElement).text(DateUtil.formatDateIso(node.initializationTimestamp));
                    $(".last-message-timestamp", nodeElement).text(DateUtil.formatDateIso(node.lastMessageReceivedTimestamp));

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


