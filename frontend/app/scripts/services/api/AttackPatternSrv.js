
(function() {
    'use strict';
    angular.module('theHiveServices')
        .service('AttackPatternSrv', function($http, $q, QuerySrv) {
            var self = this;
            var baseUrl = './api/v1/pattern';

            this.tacticsCache = {};

            this.tactics = {
                keys: [
                    'reconnaissance',
                    'resource-development',
                    'initial-access',
                    'execution',
                    'persistence',
                    'privilege-escalation',
                    'defense-evasion',
                    'credential-access',
                    'discovery',
                    'lateral-movement',
                    'collection',
                    'command-and-control',
                    'exfiltration',
                    'impact'
                ],
                values: {
                    'reconnaissance': {
                        label: 'Reconnaissance'
                    },
                    'resource-development': {
                        label: 'Resource Development'
                    },
                    'initial-access': {
                        label: 'Initial Access'
                    },
                    'execution': {
                        label: 'Execution'
                    },
                    'persistence': {
                        label: 'Persistence'
                    },
                    'privilege-escalation': {
                        label: 'Privilege Escalation'
                    },
                    'defense-evasion': {
                        label: 'Defense Evasion'
                    },
                    'credential-access': {
                        label: 'Credential Access'
                    },
                    'discovery': {
                        label: 'Discovery'
                    },
                    'lateral-movement': {
                        label: 'Lateral Movement'
                    },
                    'collection': {
                        label: 'Collection'
                    },
                    'command-and-control': {
                        label: 'Command and Control'
                    },
                    'exfiltration': {
                        label: 'Exfiltration'
                    },
                    'impact': {
                        label: 'Impact'
                    }
                }
            };

            this.list = function() {
                return QuerySrv.call('v1', [
                    { _name: 'listPattern' }
                ], {
                    name:'list-attack-patterns'
                });
            };

            this.getByTactic = function(tactic) {
                var defer = $q.defer();

                if(self.tacticsCache[tactic]) {
                    console.log('get techniques from cache for ', tactic);

                    defer.resolve(self.tacticsCache[tactic]);
                } else {
                    console.log('get techniques from server for ', tactic);

                    QuerySrv.call('v1', [
                        { _name: 'listPattern'}
                    ], {
                        name:'list-attack-patterns-for-' + tactic,
                        filter: {
                            _and: [
                                {
                                    _field: 'patternType',
                                    _value: 'attack-pattern'
                                }, {
                                    _like: {
                                        _field: 'tactics',
                                        _value: tactic
                                    }
                                },
                                {
                                    _field: 'revoked',
                                    _value: false
                                },
                                {
                                    _not: {
                                        _contains: 'parent'
                                    }
                                }
                            ]
                        },
                        sort: ['+patternId'],
                        page: {
                            from: 0,
                            to: 100,
                            extraData: ['children']
                        }
                    }).then(function(techniques) {
                        self.tacticsCache[tactic] = techniques;

                        defer.resolve(techniques);
                    });
                }

                return defer.promise;
            };

            this.get = function(id) {

                var defer = $q.defer();

                QuerySrv.call('v1', [{
                    '_name': 'getPattern',
                    'idOrName': id
                }], {
                    name:'get-attach-pattern-' + id,
                    page: {
                        from: 0,
                        to: 1,
                        extraData: [
                            'parent',
                            'children'
                        ]
                    }
                }).then(function(response) {
                    defer.resolve(response[0]);
                }).catch(function(err){
                    defer.reject(err);
                });

                return defer.promise;
            };

            this.import = function(post) {
                var postData = {
                    file: post.attachment
                };

                return $http({
                    method: 'POST',
                    url: baseUrl + '/import/attack',
                    headers: {
                        'Content-Type': undefined
                    },
                    transformRequest: function (data) {
                        var formData = new FormData(),
                            copy = angular.copy(data, {});

                        angular.forEach(data, function (value, key) {
                            if (Object.getPrototypeOf(value) instanceof Blob || Object.getPrototypeOf(value) instanceof File) {
                                formData.append(key, value);
                                delete copy[key];
                            }
                        });

                        return formData;
                    },
                    data: postData
                });
            };
        });

})();
