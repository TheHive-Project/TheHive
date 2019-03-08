(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('updatableText', function(UtilsSrv) {
        return {
            'restrict': 'E',
            'link': UtilsSrv.updatableLink,
            'templateUrl': 'views/directives/updatable-text.html',
            'scope': {
                'value': '=?',
                'onUpdate': '&',
                'active': '=?'
            }
        };
    });
})();
