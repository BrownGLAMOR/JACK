$(window).load(register);

function register() {
    var manager = prompt("Please enter your name:", null);
    if (manager != null) {
        $.ajax({
            data: {'manager': manager},
            type: 'GET',
            url: 'register'
        });
    }
}
