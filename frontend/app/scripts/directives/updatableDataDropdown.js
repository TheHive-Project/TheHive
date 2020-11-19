(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('updatableDataDropdown', function(UtilsSrv) {
            return {
                'restrict': 'E',
                'link': UtilsSrv.updatableLink,
                'templateUrl': 'views/directives/updatable-data-dropdown.html',
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
