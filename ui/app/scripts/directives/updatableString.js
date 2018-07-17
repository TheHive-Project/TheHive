(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('updatableString', function(UtilsSrv) {
            return {
                'restrict': 'E',
                'link': UtilsSrv.updatableLink,
                'templateUrl': 'views/directives/updatable-string.html',
                'scope': {
                    'value': '=?',
                    'onUpdate': '&',
                    'active': '=?',
                    'placeholder': '@'
                }
            };
        });
})();