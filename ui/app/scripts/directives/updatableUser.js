(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('updatableUser', function(UserSrv, UserInfoSrv, UtilsSrv, PSearchSrv) {
            return {
                restrict: 'E',
                link: function(scope, element, attrs, ctrl, transclude) {
                    var cached = false;

                    UtilsSrv.updatableLink(scope, element, attrs, ctrl, transclude);

                    scope.setValue = function(value) {
                        scope.value = value;
                    };
                    scope.getUserInfo = UserInfoSrv;

                    scope.$watch('updatable.updating', function(value) {

                        if(value === true && !cached) {
                            scope.userList = PSearchSrv(undefined, 'user', {
                                scope: scope,
                                baseFilter: {
                                    'status': 'Ok'
                                },
                                loadAll: true,
                                sort:  '+name',
                                skipStream: true
                            });
                            cached = true;
                        }
                    });
                },
                templateUrl: 'views/directives/updatable-user.html',
                scope: {
                    'value': '=?',
                    'onUpdate': '&',
                    'active': '=?'
                }
            };
        });
})();
