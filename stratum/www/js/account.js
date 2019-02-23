$(document).ready(function() {
    $("#authenticate-nav-button").on("click", function() {
        $("#authenticate-container").toggleClass("active", true);
        $("#create-account-container").toggleClass("active", false);
    });
    $("#create-account-nav-button").on("click", function() {
        $("#authenticate-container").toggleClass("active", false);
        $("#create-account-container").toggleClass("active", true);
    });

    $("#authenticate-email, #authenticate-password").on("keypress", function(event) {
        const key = event.which;
        if (key != KeyCodes.ENTER) { return true; }

        $(this).blur();

        $("#authenticate-button").trigger("click");

        return false;
    });

    $("#create-account-email, #create-account-password").on("keypress", function(event) {
        const key = event.which;
        if (key != KeyCodes.ENTER) { return true; }

        $(this).blur();

        $("#create-account-button").trigger("click");

        return false;
    });

    $("#authenticate-button").on("click", function() {
        Api.authenticate(
            {
                email:      $("#authenticate-email").val(),
                password:   $("#authenticate-password").val()
            },
            function(data) {
                $("#authenticate-results").text(data.wasSuccess ? "Authenticated." : data.errorMessage);

                if (data.wasSuccess) {
                    $("#authenticate-email").val("");
                    $("#authenticate-password").val("");
                }
            }
        );
    });

    $("#create-account-button").on("click", function() {
        Api.createAccount(
            {
                email:      $("#create-account-email").val(),
                password:   $("#create-account-password").val()
            },
            function(data) {
                $("#create-account-results").text(data.wasSuccess ? "Authenticated." : data.errorMessage);

                if (data.wasSuccess) {
                    $("#create-account-email").val("");
                    $("#create-account-password").val("");
                }
            }
        );
    });
});

(function() {
    Api.createAccount = function(parameters, callback) {
        const defaultParameters = { email: null, password: null };
        const apiParameters = $.extend({ }, defaultParameters, parameters);

        Http.post(Api.PREFIX + "account/create", apiParameters, callback);
    };

    Api.authenticate = function(parameters, callback) {
        const defaultParameters = { email: null, password: null };
        const apiParameters = $.extend({ }, defaultParameters, parameters);

        Http.post(Api.PREFIX + "account/authenticate", apiParameters, callback);
    };
})();
