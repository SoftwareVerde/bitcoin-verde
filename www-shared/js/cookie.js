window.Cookies = { };
window.Cookies.set = function(key, value) {
    let date = new Date();
    date.setFullYear(date.getFullYear() + 1);
    document.cookie = key + "=" + value + "; expires=" + date.toUTCString() + "; samesite=strict; path=/";
};

window.Cookies.get = function(key) {
    const value = ("; " + document.cookie);
    const parts = value.split("; " + key + "=");
    if (parts.length == 2) {
        return parts.pop().split(";").shift();
    }

    return null;
};
