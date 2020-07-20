(function() {
    'use strict';

    angular.module('theHiveControllers').controller('OrgDetailsCtrl',
        function($scope, $q, $uibModal, FilteringSrv, PaginatedQuerySrv, OrganisationSrv, NotificationSrv, UserSrv, organisation, /*users, */templates, fields, appConfig, uiConfig) {
            var self = this;

            this.uiConfig = uiConfig;
            this.org = organisation;
            //this.users = users;
            this.templates = templates;
            this.fields = fields;
            this.canChangeMfa = appConfig.config.capabilities.indexOf('mfa') !== -1;
            this.canSetPass = appConfig.config.capabilities.indexOf('setPassword') !== -1;

            this.getUserInfo = UserSrv.getCache;

            this.$onInit = function() {
                self.filtering = new FilteringSrv('user', 'user.list', {
                    version: 'v1',
                    defaults: {
                        showFilters: true,
                        showStats: false,
                        pageSize: 15,
                        //sort: ['-flag', '-startDate']
                    },
                    defaultFilter: []
                });

                self.filtering.initContext(self.org.name)
                    .then(function() {
                        self.loadUsers();

                        $scope.$watch('$vm.users.pageSize', function (newValue) {
                            self.filtering.setPageSize(newValue);
                        });
                    });
            };

            this.loadUsers = function() {

                self.users = new PaginatedQuerySrv({
                    name: 'organisation-users',
                    version: 'v1',
                    skipStream: true,
                    sort: self.filtering.context.sort,
                    loadAll: false,
                    pageSize: self.filtering.context.pageSize,
                    filter: this.filtering.buildQuery(),
                    operations: [{
                            '_name': 'getOrganisation',
                            'idOrName': self.org.name
                        },
                        {
                            '_name': 'users'
                        }
                    ],
                    config: {
                        headers: {
                            'X-Organisation': self.org.name
                        }
                    }
                });
            };

            this.showUserDialog = function(user) {
                UserSrv.openModal(user, self.org.name)
                    .then(function() {
                        self.loadUsers();
                    })
                    .catch(function(err) {
                        if (err && !_.isString(err)) {
                            NotificationSrv.error('OrgDetailsCtrl', err.data, err.status);
                        }
                    });
            };

            // Filtering
            this.toggleFilters = function () {
                this.filtering.toggleFilters();
            };

            this.filter = function () {
                self.filtering.filter().then(this.applyFilters);
            };

            this.clearFilters = function () {
                this.filtering.clearFilters()
                    .then(self.search);
            };

            this.removeFilter = function (index) {
                self.filtering.removeFilter(index)
                    .then(self.search);
            };

            this.search = function () {
                self.loadUsers();
                self.filtering.storeContext();
            };
            this.addFilterValue = function (field, value) {
                this.filtering.addFilterValue(field, value);
                this.search();
            };

        });
})();
