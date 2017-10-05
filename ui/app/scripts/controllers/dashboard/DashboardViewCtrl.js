(function() {
    'use strict';

    angular
        .module('theHiveControllers')
        .controller('DashboardViewCtrl', function(dashboard) {
            this.dashboard = dashboard;
            this.definition = JSON.parse(this.dashboard.definition) || {};

        });
})();
