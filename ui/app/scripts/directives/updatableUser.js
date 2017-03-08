(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('updatableUser', function(UserSrv, UserInfoSrv, UtilsSrv, PSearchSrv) {
            return {
                'restrict': 'E',
                'link': function(scope, element, attrs, ctrl, transclude) {
                    UtilsSrv.updatableLink(scope, element, attrs, ctrl, transclude);
                    scope.userList = PSearchSrv(undefined, 'user', {
                        scope: scope,
                        baseFilter: {
                            'status': 'Ok'
                        },
                        loadAll: true,
                        sort:  '+name'
                    });

                    scope.setValue = function(value) {
                        scope.value = value;
                    };
                    scope.getUserInfo = UserInfoSrv;
                },
                'templateUrl': 'views/directives/updatable-user.html',
                'scope': {
                    'value': '=?',
                    'onUpdate': '&',
                    'active': '=?'
                }
            };
        });
})();
