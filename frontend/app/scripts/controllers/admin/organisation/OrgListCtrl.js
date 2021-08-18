(function () {
    'use strict';

    angular.module('theHiveControllers').controller('OrgListCtrl',
        function ($scope, $q, $uibModal, PaginatedQuerySrv, OrganisationSrv, NotificationSrv, FilteringSrv, SharingProfileSrv, appConfig) {
            var self = this;

            self.appConfig = appConfig;

            this.$onInit = function () {
                self.filtering = new FilteringSrv('organisation', 'organisation.list', {
                    version: 'v1',
                    defaults: {
                        showFilters: true,
                        showStats: false,
                        pageSize: 15,
                        sort: ['-_updatedAt']
                    },
                    defaultFilter: []
                });
                self.filtering.initContext('list')
                    .then(function () {
                        self.load();

                        $scope.$watch('$vm.list.pageSize', function (newValue) {
                            self.filtering.setPageSize(newValue);
                        });
                    });
            };

            this.load = function () {

                this.list = new PaginatedQuerySrv({
                    name: 'organisations',
                    root: undefined,
                    objectType: 'organisation',
                    version: 'v1',
                    scope: $scope,
                    sort: self.filtering.context.sort,
                    loadAll: false,
                    pageSize: self.filtering.context.pageSize,
                    filter: this.filtering.buildQuery(),
                    operations: [
                        { '_name': 'listOrganisation' }
                    ]
                });
            };

            self.showNewOrg = function (mode, org) {
                var modal = $uibModal.open({
                    controller: 'OrgModalCtrl',
                    controllerAs: '$modal',
                    templateUrl: 'views/partials/admin/organisation/list/create.modal.html',
                    size: 'lg',
                    resolve: {
                        organisation: angular.copy(org),
                        mode: function () {
                            return mode;
                        }
                    }
                });

                modal.result
                    .then(function (newOrg) {
                        if (mode === 'edit') {
                            self.update(org.name, newOrg);
                        } else {
                            self.create(newOrg);
                        }
                    })
                    .catch(function (err) {
                        if (err && !_.isString(err)) {
                            NotificationSrv.error('Error', 'Unable to save the organisation.', err.status);
                        }
                    });
            };

            /**
             * Fetch org links and show a modal to allow selecting the links
             */
            self.showLinks = function (org) {
                var modalInstance = $uibModal.open({
                    //scope: $scope,
                    templateUrl: 'views/partials/admin/organisation/list/link.modal.html',
                    controller: 'OrgLinksModalCtrl',
                    controllerAs: '$modal',
                    size: 'lg',
                    resolve: {
                        organisation: function () {
                            return org;
                        },
                        organisations: function () {
                            var defer = $q.defer();

                            OrganisationSrv.list()
                                .then(function (response) {
                                    var list = _.filter(response.data, function (item) {
                                        return [OrganisationSrv.defaultOrg, org.name].indexOf(item.name) === -1;
                                    });

                                    defer.resolve(_.sortBy(list, 'name'));
                                });

                            return defer.promise;
                        },
                        links: function () {
                            return OrganisationSrv.links(org.name);
                        },
                        sharingProfiles: function () {
                            return SharingProfileSrv.all()
                        }
                    }
                });

                modalInstance.result
                    .then(function (newLinks) {
                        OrganisationSrv.setLinks(org.name, newLinks)
                            .then(function () {
                                self.load();
                                NotificationSrv.log('Organisation updated successfully', 'success');
                            })
                            .catch(function (err) {
                                NotificationSrv.error('Error', 'Organisation update failed', err.status);
                            });
                    })
                    .catch(function (err) {
                        if (err && !_.isString(err)) {
                            NotificationSrv.error('Error', 'Organisation update failed', err.status);
                        }
                    });
            };

            self.update = function (orgName, org) {
                OrganisationSrv.update(orgName, _.pick(org, 'name', 'description'))
                    .then(function (/*response*/) {
                        self.load();
                        NotificationSrv.log('Organisation updated successfully', 'success');
                    })
                    .catch(function (err) {
                        NotificationSrv.error('Error', 'Organisation update failed', err.status);
                    });
            };

            self.create = function (org) {
                OrganisationSrv.create(org)
                    .then(function (/*response*/) {
                        self.load();
                        NotificationSrv.log('Organisation created successfully', 'success');
                    })
                    .catch(function (err) {
                        NotificationSrv.error('Error', 'Organisation creation failed', err.status);
                    });
            };

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
                self.load();
                self.filtering.storeContext();
            };
            this.addFilterValue = function (field, value) {
                this.filtering.addFilterValue(field, value);
                this.search();
            };

            this.sortBy = function (sort) {
                this.list.sort = sort;
                this.list.update();
                this.filtering.setSort(sort);
            };

            this.sortByField = function (field) {
                var context = this.filtering.context;
                var currentSort = Array.isArray(context.sort) ? context.sort[0] : context.sort;
                var sort = null;

                if (currentSort.substr(1) !== field) {
                    sort = ['+' + field];
                } else {
                    sort = [(currentSort === '+' + field) ? '-' + field : '+' + field];
                }

                self.list.sort = sort;
                self.list.update();
                self.filtering.setSort(sort);
            };
        });
})();
