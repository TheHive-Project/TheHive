(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('updatableColour', function(UtilsSrv) {
            return {
                'restrict': 'E',
                'link': UtilsSrv.updatableLink,
                'templateUrl': 'views/directives/updatable-colour.html',
                'scope': {
                    'value': '=?',
                    'onUpdate': '&',
                    'active': '=?',
                    'placeholder': '@',
                    'clearable': '<?'
                }
            };
        });
})();
