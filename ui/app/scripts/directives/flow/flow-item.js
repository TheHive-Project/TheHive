(function() {
    'use strict';

    angular.module('theHiveDirectives')
        .directive('flowItem', function($uibModal, $state, $window, UserInfoSrv) {
            return {
                restrict: 'E',
                replace: true,
                scope: {
                    data: '=',
                    type: '@',
                    target: '='
                },
                link: function(scope /*, element, attrs*/ ) {
                    scope.base = scope.data.base;
                    scope.summary = scope.data.summary || {};

                    scope.operationUrl = 'views/directives/flow/operation.html';
                    scope.getContentUrl = function() {
                        return 'views/directives/flow/' + scope.type + '.html';
                    };
                    scope.gtime = function(startdate) {                        
                        return moment(startdate).toDate().getTime();
                    };
                    scope.isImage = function(contentType) {
                        return angular.isString(contentType) && contentType.indexOf('image') === 0;
                    };
                    scope.showImage = function(attachmentId, attachmentName) {
                        $uibModal.open({
                            template: '<img style="width:100%" src="/api/datastore/' + attachmentId + '" alt="' + attachmentName + '"></img>',
                            size: 'lg'
                        });
                    };
                    scope.getUserInfo = UserInfoSrv;

                    scope.openState = function(state, params) {
                        scope.openLink($state.href(state, params));
                    };

                    scope.openLink = function(link) {
                        if (scope.target) {
                            scope.target.location.href = link;
                        } else {
                            $window.location.href = link;
                        }
                    };

                    scope.isBulkOperation = function() {
                        var operation = scope.base.operation;
                        var type = scope.base.objectType;

                        var typeSummary = scope.data.summary[type];

                        if(typeSummary[operation] !== 1 || _.keys(typeSummary).length > 1 || _.keys(scope.data.summary).length > 1) {
                            return true;
                        }

                        return false;
                    };

                    scope.bulk = scope.isBulkOperation();
                },
                templateUrl: 'views/directives/flow/flow-item.html'
            };
        });
})();
