(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('StatisticSrv', function() {
            this.currentFilters = null;

            this.setFilters = function(filters) {
                this.currentFilters = filters;
            };

            this.getFilters = function() {
                return this.currentFilters;
            };

            return this;
        });
})();
