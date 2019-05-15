(function() {
    window.HashResizer = function(container) {
        if (container) {
            // If the container is specifically provided, do not use/reset the global timer...
            window.setTimeout(function() {
                window.HashResizer.update(container);
            }, 200);
            return;
        }

        window.clearTimeout(window.HashResizer.globalTimeout);
        window.HashResizer.globalTimeout = window.setTimeout(window.HashResizer.update, 200);
    }

    window.HashResizer.update = function(container) {
        const TEXT_NODE_TYPE = 3;
        container = (container || document);

        $(".hash, .transaction-hash, .block-hashes, .previous-block-hash, .difficulty .mask, .merkle-root", container).each(function() {
            const element = $(this);
            if (element.is(".no-resize")) { return; }

            window.setTimeout(function() {
                const valueElement = $(".value", element);
                const textNode = (valueElement.contents().filter(function() { return this.nodeType == TEXT_NODE_TYPE; })[0] || { });
                const textNodeContent = textNode.nodeValue;

                const originalValue = (valueElement.data("hash-value") || textNodeContent || "");
                if (originalValue.length != 64) { return; } // Sanity check...

                valueElement.data("hash-value", originalValue);
                textNode.nodeValue = originalValue;

                let isOverflowing = null;
                let truncatedValue = originalValue;
                do {
                    isOverflowing = ( (element[0].offsetHeight < element[0].scrollHeight) || (element[0].offsetWidth < element[0].scrollWidth) );
                    if (isOverflowing) {
                        const length = truncatedValue.length;
                        if (length < 4) { break; }

                        truncatedValue = truncatedValue.substr(0, ((length / 2) - 2)) + "..." + truncatedValue.substr((length / 2) + 2);
                        textNode.nodeValue = truncatedValue;
                    }
                } while (isOverflowing);
            }, 0);
        });
    };

    $(window).on("load resize", function() {
        window.HashResizer();
    });
})();
