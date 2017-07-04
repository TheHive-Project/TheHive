(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('updatableBoolean', function(UtilsSrv) {
            return {
                'restrict': 'E',
                'link': UtilsSrv.updatableLink,
                'templateUrl': 'views/directives/updatable-boolean.html',
                'scope': {
                    'value': '=?',
                    'onUpdate': '&',
                    'active': '=?',
                    'placeholder': '@',
                    'trueText': '@?',
                    'falseText': '@?'
                }
            };
        });
})();
