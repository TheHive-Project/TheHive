(function() {
    'use strict';

    angular.module('theHiveFilters').filter('escape', function() {
        return window.encodeURIComponent;
    });
})();
