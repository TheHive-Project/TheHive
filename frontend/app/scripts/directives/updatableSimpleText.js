(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('updatableSimpleText', function(UtilsSrv) {
            return {
                'restrict': 'E',
                'link': UtilsSrv.updatableLink,
                'templateUrl': 'views/directives/updatable-simple-text.html',
                'scope': {
                    'inputType': '@',
                    'value': '=?',
                    'onUpdate': '&',
                    'active': '=?',
                    'placeholder': '@',
                    'clearable': '<?'
                }
            };
        });
})();
