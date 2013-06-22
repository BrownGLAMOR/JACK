$(document).ready(function() {
    $('#place_bid_form').submit(function() {
        // TODO: Check if bid is under budget
        $.ajax({
            data: $(this).serialize(),
            type: 'POST',
            url: 'place_bid'
        });
        return false;
    });
});
