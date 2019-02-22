class DocumentationUi {
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
            DocumentationUi.search(hash);
            return false;  
        }
    });   
});

