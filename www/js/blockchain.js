class BlockchainUi {
    static search(objectHash) {
        document.location.href = "/?search=" + window.encodeURIComponent(objectHash);
    }

    static renderBlockchainMetadata(blockchainMetadata) {

        const sortedBlockCounts = [];

        const nodes = [];
        for (let i in blockchainMetadata) {
            const metadata = blockchainMetadata[i];

            nodes.push({
                id:     metadata.blockchainSegmentId,
                shape:  "dot",
                label:  (metadata.minBlockHeight + " - " + metadata.maxBlockHeight),
                value:  metadata.blockCount,
                title:  ("Id " + metadata.blockchainSegmentId + ": " + metadata.blockCount + " blocks")
            });

            sortedBlockCounts.push(metadata.blockCount);
        }


        { // Sort the blockCounts and make them unique...
            sortedBlockCounts.sort(function(a, b){ return (a - b); });
            // const medianBlockCount = (sortedBlockCounts.length > 0 ? sortedBlockCounts[sortedBlockCounts.length / 2] : 0);
            const copiedArray = sortedBlockCounts.slice();
            sortedBlockCounts.length = 0;
            let previousBlockCountValue = null;
            for (let i in copiedArray) {
                const blockCountValue = copiedArray[i];
                if (previousBlockCountValue != blockCountValue) {
                    sortedBlockCounts.push(blockCountValue);
                }
                previousBlockCountValue = blockCountValue;
            }
        }

        const buckets = [];
        { // Create scaling buckets for the custom scaling function...
            const bucketCount = 5;
            const itemsPerBucket = Math.ceil(sortedBlockCounts.length / bucketCount);
            for (let i = 0; i < bucketCount; i += 1) {
                buckets.push({ min: Number.MAX_VALUE, max: 0 });
            }

            for (let i in sortedBlockCounts) {
                const bucket = buckets[Math.floor(i / itemsPerBucket)];
                const blockCount = sortedBlockCounts[i];

                bucket.min = (blockCount < bucket.min ? blockCount : bucket.min);
                bucket.max = (blockCount > bucket.max ? blockCount : bucket.max);
            }
        }

        const edges = [];
        for (let i in blockchainMetadata) {
            const metadata = blockchainMetadata[i];

            if (metadata.parentBlockchainSegmentId) {
                edges.push({
                    from:   metadata.parentBlockchainSegmentId,
                    to:     metadata.blockchainSegmentId,
                    title:  (metadata.blockCount + " blocks")
                });
            }
        }

        const data = {
            nodes: nodes,
            edges: edges
        };

        const container = document.getElementById("blockchain-metadata");

        const options = {
            layout: {
                hierarchical: {
                    direction: (window.innerWidth > window.innerHeight ? "LR" : "DU"),
                    sortMethod: "directed"
                }
            },
            nodes: {
                borderWidth: 1,
                scaling: {
                    min: 10,
                    max: 50,
                    label: {
                        min: 10,
                        max: 10
                    },
                    customScalingFunction: function(min, max, total, value) {
                        for (let i = 0; i < buckets.length; i += 1) {
                            const bucket = buckets[i];
                            if (value <= bucket.max && value >= bucket.min) {
                                const bucketScale = (bucket.max == bucket.min ? 1.0 : ((value - bucket.min) / (bucket.max - bucket.min)));
                                return ((1.0 / buckets.length) * (bucketScale + i));
                            }
                        }

                        return 0.5;
                    }
                },
                color: {
                    border: "#AAAAAA",
                    background: "#EEEEEE"
                },
                font: {
                    color:"#202020"
                }
            },
            edges: {
                color: "#AAAAAA"
            }
        };

        const network = new vis.Network(container, data, options);
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
            BlockchainUi.search(hash);
            return false;  
        }
    });   


    Api.getBlockchainMetadata({ }, function(data) {
        if (! data.wasSuccess) {
            console.log(data.errorMessage);
            return;
        }

        const blockchainMetadata = data.blockchainMetadata;
        BlockchainUi.renderBlockchainMetadata(blockchainMetadata);
    });

});


