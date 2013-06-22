var count = 0;
var counter;

function secsToString(secs) {
    var minutes = Math.floor(secs / 60);
    var seconds = secs - 60 * minutes;
    var seconds_string = seconds + "";
    if (seconds_string.length < 2) {
        seconds_string = "0" + seconds_string;
    }
    return minutes + ":" + seconds_string;
}

function reset_timer(newCount) {
    if (newCount > count) {
        clearInterval(counter);
        count = newCount;
        counter = setInterval(timer, 1000);
        document.getElementById("auction_timer").innerHTML = secsToString(count);
    }
}

function timer() {
    count = count - 1;
    if (count < 0) {
        clearInterval(counter);
        return;
    }
    document.getElementById("auction_timer").innerHTML = secsToString(count);
}
