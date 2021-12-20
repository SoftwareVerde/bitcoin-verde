$(document).ready(function() {
    Ui.Admin.setAuthenticated(false);
    Api.Admin.validateAuthentication({ }, function(response) {
        Ui.Admin.setAuthenticated(response.wasSuccess);
    });
});

(function() {
    Api.Admin = { };

    Api.Admin.validateAuthentication = function(parameters, callback) {
        const defaultParameters = { };
        const apiParameters = $.extend({ }, defaultParameters, parameters);

        Http.get(Api.PREFIX + "admin/validate", apiParameters, callback);
    };

    Api.Admin.authenticate = function(parameters, callback) {
        const defaultParameters = { email: null, password: null };
        const apiParameters = $.extend({ }, defaultParameters, parameters);

        Http.post(Api.PREFIX + "admin/authenticate", apiParameters, callback);
    };

    Api.Admin.unauthenticate = function(parameters, callback) {
        const defaultParameters = { };
        const apiParameters = $.extend({ }, defaultParameters, parameters);

        Http.post(Api.PREFIX + "admin/unauthenticate", apiParameters, callback);
    };

    Api.Admin.getPayoutAddress = function(parameters, callback) {
        const defaultParameters = { };
        const apiParameters = $.extend({ }, defaultParameters, parameters);

        Http.get(Api.PREFIX + "admin/address", apiParameters, callback);
    };

    Api.Admin.getWorkerDifficulty = function(parameters, callback) {
        const defaultParameters = { };
        const apiParameters = $.extend({ }, defaultParameters, parameters);

        Http.get(Api.PREFIX + "admin/worker/difficulty", apiParameters, callback);
    };

    Api.Admin.setWorkerDifficulty = function(parameters, callback) {
        const defaultParameters = { shareDifficulty: null };
        const apiParameters = $.extend({ }, defaultParameters, parameters);

        Http.post(Api.PREFIX + "admin/worker/difficulty", apiParameters, callback);
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

    Ui.Admin = { };

    Ui.Admin.showUnauthenticatedNavigation = function() {
        const templates = $("#templates");
        const navigationContainer = $("#main .navigation ul");

        const view = $("ul.unauthenticated-navigation", templates).clone();
        const navItems = view.children();

        $(".authenticate-nav-button", view).on("click", function() {
            Ui.Admin.showAuthenticateView();
        });
        $(".create-admin-nav-button", view).on("click", function() {
            Ui.Admin.showCreateAdminView();
        });

        navigationContainer.empty();
        navigationContainer.append(navItems);
    };

    Ui.Admin.showAuthenticateView = function() {
        const templates = $("#templates");
        const viewContainer = $("#main #view-container");

        const view = $(".authenticate-container", templates).clone();

        const button = $(".submit-button", view);

        button.on("click", function() {
            Api.Admin.authenticate(
                {
                    email:      $("input.authenticate-email", view).val(),
                    password:   $("input.authenticate-password", view).val()
                },
                function(data) {
                    $(".results", view).text(data.wasSuccess ? "Authenticated." : data.errorMessage);

                    if (data.wasSuccess) {
                        $(".authenticate-email", view).val("");
                        $(".authenticate-password", view).val("");

                        Ui.Admin.setAuthenticated(true);
                    }
                }
            );
        });

        onEnterSubmit($("input", view), button);

        viewContainer.empty();
        viewContainer.append(view);
    };

    Ui.Admin.showCreateAdminView = function() {
        const templates = $("#templates");
        const viewContainer = $("#main #view-container");

        const view = $(".create-admin-container", templates).clone();

        const button = $(".submit-button", view);

        button.on("click", function() {
            Api.Admin.createAdmin(
                {
                    email:      $("input.create-admin-email", view).val(),
                    password:   $("input.create-admin-password", view).val()
                },
                function(data) {
                    $(".results", view).text(data.wasSuccess ? "Authenticated." : data.errorMessage);

                    if (data.wasSuccess) {
                        $(".create-admin-email", view).val("");
                        $(".create-admin-password", view).val("");

                        Ui.Admin.setAuthenticated(true);
                    }
                }
            );
        });

        onEnterSubmit($("input", view), button);

        viewContainer.empty();
        viewContainer.append(view);
    };

    Ui.Admin.showAuthenticatedNavigation = function() {
        const templates = $("#templates");
        const navigationContainer = $("#main .navigation ul");

        const view = $("ul.authenticated-navigation", templates).clone();
        const navItems = view.children();

        $(".set-payout-address-nav-button", view).on("click", function() {
            Ui.Admin.showSetAddressView();
        });

        $(".update-password-nav-button", view).on("click", function() {
            Ui.Admin.showUpdatePasswordView();
        });

        $(".manage-workers-nav-button", view).on("click", function() {
            Ui.Admin.showManageWorkersView();
        });

        $(".unauthenticate-nav-button", view).on("click", function() {
            Api.Admin.unauthenticate({ }, function(response) {
                Ui.Admin.showUnauthenticatedNavigation();
                Ui.Admin.showAuthenticateView();
            });
        });

        navigationContainer.empty();
        navigationContainer.append(navItems);
    };

    Ui.Admin.showUpdatePasswordView = function() {
        const templates = $("#templates");
        const viewContainer = $("#main #view-container");

        const view = $(".update-password-container", templates).clone();

        const timeoutContainer = this;

        const button = $(".submit-button", view);

        button.on("click", function() {
            const resultsView = $(".results", view);
            window.clearTimeout(timeoutContainer.timeout);

            if ($(".new-password", view).val() != $(".confirm-new-password", view).val()) {
                resultsView.text("Passwords do not match.");
                timeoutContainer.timeout = window.setTimeout(function() {
                    resultsView.text("");
                }, 3000);

                return;
            }

            Api.Admin.updatePassword(
                {
                    password: $("input.password", view).val(),
                    newPassword: $("input.new-password", view).val()
                },
                function(response) {
                    let message = "Password updated.";
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

        onEnterSubmit($("input", view), button);

        viewContainer.empty();
        viewContainer.append(view);
    };

    Ui.Admin.showSetAddressView = function() {
        const templates = $("#templates");
        const viewContainer = $("#main #view-container");

        const view = $(".set-address-container", templates).clone();

        const timeoutContainer = this;

        const button = $(".submit-button", view);

        button.on("click", function() {
            const resultsView = $(".results", view);
            window.clearTimeout(timeoutContainer.timeout);
            Api.Admin.setPayoutAddress(
                {
                    address: $("input.address", view).val()
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

        onEnterSubmit($("input", view), button);

        viewContainer.empty();
        viewContainer.append(view);

        Api.Admin.getPayoutAddress({ }, function(response) {
            $("input.address", view).val(response.address);
        });
    };

    Ui.Admin.showManageWorkersView = function() {
        const templates = $("#templates");
        const viewContainer = $("#main #view-container");

        const view = $(".manage-workers-container", templates).clone();

        const timeoutContainer = this;

        const button = $(".submit-button", view);

        button.on("click", function() {
            const resultsView = $(".results", view);
            window.clearTimeout(timeoutContainer.timeout);

            Api.Admin.setWorkerDifficulty(
                {
                    shareDifficulty: $("input.worker-difficulty", view).val()
                },
                function(response) {
                    let message = "Difficulty updated.";
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

        onEnterSubmit($("input", view), button);

        viewContainer.empty();
        viewContainer.append(view);

        Api.Admin.getWorkerDifficulty({ }, function(response) {
            $("input.worker-difficulty", view).val(response.shareDifficulty);
        });
    };

    Ui.Admin.setAuthenticated = function(isAuthenticated) {
        const viewContainer = $("#main #view-container");

        if (isAuthenticated) {
            Ui.Admin.showAuthenticatedNavigation();
            Ui.Admin.showSetAddressView();
        }
        else {
            Ui.Admin.showUnauthenticatedNavigation();
            Ui.Admin.showAuthenticateView();
        }
    };
})();
