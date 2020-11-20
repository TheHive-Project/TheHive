(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('updatableTags', function(UtilsSrv) {
            return {
                'restrict': 'E',
                'link': UtilsSrv.updatableLink,
                'templateUrl': 'views/directives/updatable-tags.html',
                'scope': {
                    'value': '=?',
                    'onUpdate': '&',
                    'active': '=?',
                    'source': '=',
                    'clearable': '<?'
                }
            };
        });
})();
