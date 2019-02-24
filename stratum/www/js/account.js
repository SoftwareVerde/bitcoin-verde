$(document).ready(function() {
    Ui.Account.setAuthenticated(false);
    Api.Account.validateAuthentication({ }, function(response) {
        Ui.Account.setAuthenticated(response.wasSuccess);
    });
});

(function() {
    Api.Account = { };

    Api.Account.createAccount = function(parameters, callback) {
        const defaultParameters = { email: null, password: null };
        const apiParameters = $.extend({ }, defaultParameters, parameters);

        Http.post(Api.PREFIX + "account/create", apiParameters, callback);
    };

    Api.Account.validateAuthentication = function(parameters, callback) {
        const defaultParameters = { };
        const apiParameters = $.extend({ }, defaultParameters, parameters);

        Http.post(Api.PREFIX + "account/validate", apiParameters, callback);
    };

    Api.Account.authenticate = function(parameters, callback) {
        const defaultParameters = { email: null, password: null };
        const apiParameters = $.extend({ }, defaultParameters, parameters);

        Http.post(Api.PREFIX + "account/authenticate", apiParameters, callback);
    };

    Api.Account.unauthenticate = function(parameters, callback) {
        const defaultParameters = { email: null, password: null };
        const apiParameters = $.extend({ }, defaultParameters, parameters);

        Http.post(Api.PREFIX + "account/unauthenticate", apiParameters, callback);
    };

    Api.Account.getPayoutAddress = function(parameters, callback) {
        const defaultParameters = { };
        const apiParameters = $.extend({ }, defaultParameters, parameters);

        Http.get(Api.PREFIX + "account/address", apiParameters, callback);
    };

    Api.Account.setPayoutAddress = function(parameters, callback) {
        const defaultParameters = { address: null };
        const apiParameters = $.extend({ }, defaultParameters, parameters);

        Http.post(Api.PREFIX + "account/address", apiParameters, callback);
    };
})();

(function() {
    const onEnterSubmit = function(input, button) {
        input.on("keypress", function(event) {
            const key = event.which;
            if (key != KeyCodes.ENTER) { return true; }

            $(this).blur();

            button.trigger("click");

            return false;
        });
    };

    Ui.Account = { };

    Ui.Account.showUnauthenticatedNavigation = function() {
        const templates = $("#templates");
        const navigationContainer = $("#main .navigation ul");

        const view = $("ul.unauthenticated-navigation", templates).clone();
        const navItems = view.children();

        $(".authenticate-nav-button", view).on("click", function() {
            Ui.Account.showAuthenticateView();
        });
        $(".create-account-nav-button", view).on("click", function() {
            Ui.Account.showCreateAccountView();
        });

        navigationContainer.empty();
        navigationContainer.append(navItems);
    };

    Ui.Account.showAuthenticateView = function() {
        const templates = $("#templates");
        const viewContainer = $("#main #view-container");

        const view = $(".authenticate-container", templates).clone();

        onEnterSubmit($("input", view), $(".authenticate-button", view));

        $(".authenticate-button", view).on("click", function() {
            Api.Account.authenticate(
                {
                    email:      $(".authenticate-email", view).val(),
                    password:   $(".authenticate-password", view).val()
                },
                function(data) {
                    $(".authenticate-results", view).text(data.wasSuccess ? "Authenticated." : data.errorMessage);

                    if (data.wasSuccess) {
                        $(".authenticate-email", view).val("");
                        $(".authenticate-password", view).val("");

                        Ui.Account.setAuthenticated(true);
                    }
                }
            );
        });

        viewContainer.empty();
        viewContainer.append(view);
    };

    Ui.Account.showCreateAccountView = function() {
        const templates = $("#templates");
        const viewContainer = $("#main #view-container");

        const view = $(".create-account-container", templates).clone();

        onEnterSubmit($("input", view), $(".create-account-button", view));

        $(".create-account-button", view).on("click", function() {
            Api.Account.createAccount(
                {
                    email:      $(".create-account-email", view).val(),
                    password:   $(".create-account-password", view).val()
                },
                function(data) {
                    $(".create-account-results", view).text(data.wasSuccess ? "Authenticated." : data.errorMessage);

                    if (data.wasSuccess) {
                        $(".create-account-email", view).val("");
                        $(".create-account-password", view).val("");

                        Ui.Account.setAuthenticated(true);
                    }
                }
            );
        });

        viewContainer.empty();
        viewContainer.append(view);
    };

    Ui.Account.showAuthenticatedNavigation = function() {
        const templates = $("#templates");
        const navigationContainer = $("#main .navigation ul");

        const view = $("ul.authenticated-navigation", templates).clone();
        const navItems = view.children();

        $(".unauthenticate-nav-button", view).on("click", function() {
            Api.Account.unauthenticate({ }, function(response) {
                Ui.Account.showUnauthenticatedNavigation();
                Ui.Account.showAuthenticateView();
            });
        });

        navigationContainer.empty();
        navigationContainer.append(navItems);
    };

    Ui.Account.showSetAddressView = function() {
        const templates = $("#templates");
        const viewContainer = $("#main #view-container");

        const view = $(".set-address-container", templates).clone();

        const timeoutContainer = this;

        $(".set-address-button", view).on("click", function() {
            const resultsView = $(".set-address-results", view);
            window.clearTimeout(timeoutContainer.timeout);
            Api.Account.setPayoutAddress(
                {
                    address: $(".address", view).val()
                },
                function(response) {
                    let message = "Address updated.";
                    if (! response.wasSuccess) {
                        message = response.errorMessage;
                    }

                    resultsView.text(message);
                    timeoutContainer.timeout = window.setTimeout(function() {
                        resultsView.text("");
                    }, 3000);
                }
            );
        });

        onEnterSubmit($("input", view), $(".create-account-button", view));

        viewContainer.empty();
        viewContainer.append(view);

        Api.Account.getPayoutAddress({ }, function(response) {
            $(".address", view).val(response.address);
        });
    };

    Ui.Account.setAuthenticated = function(isAuthenticated) {
        const viewContainer = $("#main #view-container");

        if (isAuthenticated) {
            Ui.Account.showAuthenticatedNavigation();
            Ui.Account.showSetAddressView();
        }
        else {
            Ui.Account.showUnauthenticatedNavigation();
            Ui.Account.showAuthenticateView();
        }
    };
})();
