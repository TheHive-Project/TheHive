(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('AppLayoutSrv', function($state, $window, $rootScope, localStorageService) {
            var key = 'app-layout';

            this.layout = {};

            this.init = function() {
                this.layout = localStorageService.get(key) || {
                    showFlow: true,
                    caseCustomFieldColumns: 3,
                    alertCustomFieldColumns: 2
                };

                this.saveLayout();
            };

            this.saveLayout = function() {
                localStorageService.set(key, this.layout);
                $rootScope.appLayout = this.layout;
            };

            this.getLayout = function() {
                return this.layout;
            };

            this.showFlow = function(show) {
                this.layout.showFlow = show;
                this.saveLayout();
            };

            this.groupTasks = function(group) {
                this.layout.groupTasks = group;
                this.saveLayout();
            };

            this.caseCustomFields = function(columns) {
                this.layout.caseCustomFieldColumns = columns;
                this.saveLayout();
            };

            this.alertCustomFields = function(columns) {
                this.layout.alertCustomFieldColumns = columns;
                this.saveLayout();
            };

            this.detachFlow = function(/*root*/) {
                this.showFlow(false);
                $window.open($state.href('live'), 'TheHiveLive', 'width=500,height=700,menubar=no,status=no,toolbar=no,location=no,scrollbars=yes');
            };
        });
})();
