(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('updatableNumber', function(UtilsSrv) {
            return {
                'restrict': 'E',
                'link': UtilsSrv.updatableLink,
                'templateUrl': 'views/directives/updatable-number.html',
                'scope': {
                    'value': '=?',
                    'onUpdate': '&',
                    'active': '=?',
                    'placeholder': '@'
                }
            };
        });
})();
