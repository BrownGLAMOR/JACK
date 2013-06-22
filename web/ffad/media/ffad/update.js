$(document).ready(get_updates);

var INTERVAL = 1000;         // Time in ms between update requests
var MIN_NUM_ROWS = 13;       // The minimum numbder of rows in the table

var manager_timestamp = {};  // Last time we got a manager update
var player_timestamp = {};   // Last time we got a player update

/**
 * There are three major types of updates that we query for: updates to
 * managers, updates to players, and updates to teams. Each type of update has a
 * corresponding ajax get request that the web server is responsible for
 * handling. See the individual functions for details.
 */
function get_updates() {
    get_manager_updates();
    get_player_updates();
    get_team(); //TODO: Should do something smarter here

    // Queue another update
    setTimeout(get_updates, INTERVAL);
}

/**
 * This function queries the web server for changes made to any managers since
 * the last time that we got an update. There are really only two fields that we
 * are most concerned about updating: the budget and the value.
 */
function get_manager_updates() {

    /**
     * Updates the manager in the manager container with the specified id.
     * Returns true on success and false if the manager has not been previously
     * added to the container.
     */
    function update_manager(id, manager) {
        var entry = $('.manager_container div[id="' + id + '"]');
        if (!entry.length) {
            return false;
        }
        $('.name', entry).html(manager.name);
        $('.budget', entry).html(manager.budget);
        $('.value', entry).html(manager.value);
        return true;
    }

    /**
     * Adds the manager with the specified id to the manager container. Choose
     * the managers style based on the modulo of the id--this does not match up
     * perfectly with what we are doing in the django template, but it is close
     * enough.
     */
    function add_manager(id, manager) {
        var style = 'style' + (id % 10).toString()
        var html = [
            '<div class="manager ' + style + '" id="' + id + '">',
                '<div class="name">' + manager.name + '</div>',
                '<img class="picture" src="' + STATIC_URL + 'ffad/stripes.png">',
                '<div class="budget">' + manager.budget + '</div>',
                '<div class="value">' + manager.value + '</div>',
            '</div>'].join('\n')
        $('.manager_container').append(html)
    }

    /**
     * Send the ajax request to the web server and process the JSON response.
     */
    $.getJSON('get_manager_updates', manager_timestamp,
        function(response) {
            $.each(response.managers, function(i, m) {

                // Try to update this manager in the manager container. If no
                // such manager exists, then add him/her to the container.

                if (!update_manager(m.pk, m.fields)) {
                    add_manager(m.pk, m.fields);
                }
            });

            manager_timestamp = { 'since': response.time };
        }
    );
}

/**
 * This function queries the web server for changes that have been made to
 * players since the last time that we updated. There are multiple fields that
 * we are potentially interested in updating for each player. The most important
 * are timer, manager, and bid. Though the other values can change, we expect
 * that they will usually be mostly static.
 */
function get_player_updates() {

    /**
     * Updates the player in the player container with the specified id.
     * Returns true on success and false if the player has not been previously
     * added to the container.
     */
    function update_player(id, player) {
        var row = $('.player_container tr[id="player' + id + '"]');
        if (!row.length) {
            return false;
        }

        // Update the draft status of the player

        if (player.manager) {
            row.addClass('drafted');
        } else {
            row.removeClass('drafted');
        }

        if (player.timer) {
            row.addClass('up_for_auction');
        } else {
            row.removeClass('up_for_auction');
        }

        $('.order', row).html(player.order);
        $('.position', row).html(player.position);
        $('.name', row).html(player.name);
        $('.value', row).html(player.value);
        return true;
    }

    /**
     * Adds the player with the specified id to the player container.
     */
    function add_player(id, player) {
        var classes = (player.manager ? 'drafted': '') +
                      (player.timer ? 'up_for_auction': '');
        var html = [
            '<tr class="' + classes + '" id="player' + id + '">',
                '<td class="order">' + player.order + '</td>',
                '<td class="position">' + player.position + '</td>',
                '<td class="name">' + player.name + '</td>',
                '<td class="value">' + player.value + '</td>',
            '</tr>'].join('\n');
        $('.player_container tbody:last').append(html);
    }

    /**
     * This function updates the auction to reflect the status of the speficied
     * player. It not only updates the auction fields, but resets the timer and
     * highlights the current high bidder.
     */
    function update_auction(resp_time, managers, player) {

        $('#auction_name').html(player.name);
        $('#auction_position').html(player.position);
        $('#auction_value').html(player.value);

        $('#winning_bid').html(player.bid);

        if (player.manager) {
            for (var i = 0; i < managers.length; ++i) {
                if (managers[i].pk == player.manager) {
                    var name = managers[i].fields.name;
                    $('#winning_bidder').html(name);
                    break;
                }
            }
        } else {
            $('#winning_bidder').html('');
        }

        //TODO: Calculate an estimated value for the team
        //$('#winning_value').html();
        //$('#winning_new_value').html(player.value);

        // Highlight the high bidder

        $('.manager').each(function(i) {
            if ($(this).attr('id') == player.manager) {
                $(this).addClass('top_bid');
            } else {
                $(this).removeClass('top_bid');
            }
        });

        // Calculate the remaining time left in the auction. Don't rely on the
        // clients clock here and instead use the time of the response as 'now'.

        start = new Date(player.modified);
        now = new Date(resp_time);
        expired = Math.round((now - start) / 1000);

        reset_timer(player.timer - expired);
    }

    /**
     * Send the ajax request to the web server and process the JSON response.
     */
    $.getJSON('get_player_updates', player_timestamp,
        function(response) {
            $.each(response.players, function(i, p) {

                // Try to update this player in the player container. If no
                // such player exists, then add him/her to the container.

                if (!update_player(p.pk, p.fields)) {
                    add_player(p.pk, p.fields);
                }

                // A non null timer means that this player is currently up for
                // auction--there should only be one such player, so update the
                // auction.

                if (p.fields.timer) {
                    update_auction(response.time, response.managers, p.fields);
                }
            });

            player_timestamp = { 'since': response.time };
        }
    );
}

/**
 * This function queries the web server for the currently selected manager's
 * team. It always receives the full team and replaces whatever team had been
 * there previously;
 */
function get_team() {
    $.getJSON('get_team',
        function(response) {
            $('.team_container tbody:last').empty();
            $.each(response.team, function(i, slot) {
                var html = [
                    '<tr>',
                        '<td class="position">' + slot[0] + '</td>',
                        '<td class="name">' + slot[1] + '</td>',
                        '<td class="value">' + slot[2] + '</td>',
                    '</tr>'].join('\n');
                $('.team_container tbody:last').append(html);
            });

            // Append empty rows to fill out the table

            var html = ['<tr>',
                        '<td class="position">&nbsp</td>',
                        '<td class="name"></td>',
                        '<td class="value"></td>',
                        '</tr>'].join('\n');
            for (var i = response.team.length; i < MIN_NUM_ROWS; ++i) {
                $('.team_container tbody:last').append(html);
            }
        }
    )
}

