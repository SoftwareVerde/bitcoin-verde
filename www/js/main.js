class Http {
    static get(url, data, callbackFunction) {
        const query = Object.keys(data).map(key => encodeURIComponent(key) + "=" + encodeURIComponent(data[key])).join("&");

        const request = new Request(
            url + "?" + query,
            {
                method:         "GET",
                credentials:    "include",
            }
        );

        window.fetch(request, { credentials: "same-origin" })
            .then(function(response) {
                return response.json();
            })
            .then(function(json) {
                if (typeof callbackFunction == "function") {
                    callbackFunction(json);
                }
            });
    }

    static post(url, data, callbackFunction) {
        const request = new Request(
            url,
            {
                method:         "POST",
                credentials:    "include",
                body:           JSON.stringify(data)
            }
        );

        window.fetch(request, { credentials: "same-origin" })
            .then(function(response) {
                return response.json();
            })
            .then(function(json) {
                if (typeof callbackFunction == "function") {
                    callbackFunction(json);
                }
            });
    }
}

class Api {
    static search(hash, callback) {
        Http.get(Api.PREFIX + "search", { hash: hash }, callback);
    }
}
Api.PREFIX = "/api/v1/";

class Constants {
    static get BLOCK() { return "BLOCK"; }
    static get TRANSACTION() { return "TRANSACTION"; }
}

class KeyCodes {
    static get ENTER() { return 13; }
}

class UI {
    static renderBlock(block) {
        console.log(block);
    }

    static renderTransaction(transaction) {
        console.log(transaction);
    }
}

$(document).ready(function() {
    const searchInput = $("#search");
    const loadingImage = $("#search-loading-image");
    searchInput.keypress(function(event) {
        const key = event.which;

        if (key == KeyCodes.ENTER) {
            loadingImage.css("visibility", "visible");
            Api.search(searchInput.val(), function(data) {
                loadingImage.css("visibility", "hidden");
                console.log(data);

                const wasSuccess = data.wasSuccess;
                const errorMessage = data.errorMessage;
                const objectType = data.objectType;
                const object = data.object;

                if (wasSuccess) {
                    if (objectType == Constants.BLOCK) {
                        UI.renderBlock(object);
                    }
                    else {
                        UI.renderTransaction(object);
                    }
                }
                else {
                   console.log(errorMessage);
                }
            });

            return false;  
        }
    });   
});

